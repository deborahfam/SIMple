package com.simple.mobile.presentation

import com.simple.mobile.domain.transfer.*
import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.simple.mobile.SimpleApplication
import com.simple.mobile.data.SimRepository
import com.simple.mobile.data.telephony.SmsSyncStatus
import com.simple.mobile.data.local.EncryptedReadingStore
import com.simple.mobile.data.parser.CubacelParser
import com.simple.mobile.data.telephony.UssdDispatcher
import com.simple.mobile.domain.carrier.CubacelQuery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class Line(val subscription: Int, val slot: Int, val carrier: String, val cubacel: Boolean,
                val active: Boolean = true, val nickname: String = "") {
    val label: String get() = nickname.ifBlank {
        if (active) "SIM ${slot + 1} · ${carrier.ifBlank { "Operador" }}" else "Línea guardada $subscription"
    }
}
data class DashboardState(
    val lines: List<Line> = emptyList(), val selected: Int = -1,
    val entries: List<EncryptedReadingStore.Entry> = emptyList(),
    val bankNotices: List<com.simple.mobile.data.recharge.BankRechargeNotice> = emptyList(),
    val bankNoticeError: Boolean = false,
    val unreadableCount: Int = 0, val loading: Boolean = true, val busy: Boolean = false,
    val message: String? = null, val permission: Boolean = false,
    val storageReady: Boolean = false,
    val pendingQueries: Map<Int, List<CubacelQuery>> = emptyMap(),
    val activeQuery: CubacelQuery? = null, val queryNumber: Int = 0, val queryTotal: Int = 0,
    val querySubscription: Int = -1, val stopRequested: Boolean = false,
    val smsStatus: SmsSyncStatus = SmsSyncStatus(), val smsReadAllowed: Boolean = false, val smsReceiveAllowed: Boolean = false,
    val linePhones: Map<Int, String> = emptyMap(), val nicknames: Map<Int, String> = emptyMap(), val managingLine: Boolean = false,
    val recipients: List<Recipient> = emptyList(), val transfers: List<TransferRecord> = emptyList(),
    val transferReady: Boolean = false, val transferSaving: Boolean = false,
    val pickedContact: Pair<String, String>? = null, val refreshOffer: Int? = null,
    val rechargeDates: Map<Int, java.time.LocalDate> = emptyMap(),
    val mobileUsage: com.simple.mobile.data.statistics.MobileUsageState = com.simple.mobile.data.statistics.MobileUsageState(),
    val remindersEnabled: Boolean = false, val notificationsAllowed: Boolean = false
 ) {
    val displayLines: List<Line> get() = LineCatalog.visible(lines, (linePhones.keys + entries.map { it.subscription } + transfers.map { it.subscription } + bankNotices.map { it.receivingSubscription }).toSet(), nicknames)
    fun isQuerying(query: CubacelQuery): Boolean = busy && querySubscription == selected && activeQuery == query
}

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(DashboardState())
    val state = mutable.asStateFlow()
    private val app = application as SimpleApplication
    private val worker = app.worker
    private val store = app.store
    private val parser = CubacelParser()
    private var request: CompletableFuture<String>? = null
    @Volatile private var disposed = false
    init {
        loadHistory()
        loadTransfers()
        viewModelScope.launch { app.bankNotices.collect { notices -> mutable.update { old -> old.copy(bankNotices = notices).let { it.copy(selected = LineCatalog.selection(old.selected, it.displayLines)) } } } }
        viewModelScope.launch { app.bankNoticeError.collect { failed -> mutable.update { it.copy(bankNoticeError = failed) } } }
        viewModelScope.launch { app.smsImport.state.collect { status -> mutable.update { it.copy(smsStatus = status) } } }
        viewModelScope.launch { app.readings.collect { values -> mutable.update { old -> old.copy(entries = values, unreadableCount = store.unreadableCount).let { it.copy(selected = LineCatalog.selection(old.selected, it.displayLines)) } } } }
    }
    private fun loadTransfers() {
        worker.execute {
            try {
                val recipients = store.recipients(); val transfers = store.transfers()
                mutable.update { old -> old.copy(recipients = recipients, transfers = transfers, transferReady = true).let { it.copy(selected = LineCatalog.selection(old.selected, it.displayLines)) } }
            } catch (_: Exception) {
                mutable.update { it.copy(transferReady = false) }
                notify("No se pudieron abrir los destinatarios o transferencias. Reintenta el almacenamiento desde Ajustes.")
            }
        }
    }
    fun pickedContact(name: String, phone: String) { mutable.update { it.copy(pickedContact = name to phone) } }
    fun consumeContact() { mutable.update { it.copy(pickedContact = null) } }
    fun offerRefresh(subscription: Int) { mutable.update { it.copy(refreshOffer = subscription) } }
    fun dismissRefreshOffer() { mutable.update { it.copy(refreshOffer = null) } }
    private fun transferMutation(action: () -> Unit) {
        if (mutable.value.transferSaving || mutable.value.managingLine || !mutable.value.transferReady) return
        mutable.update { it.copy(transferSaving = true) }
        worker.execute {
            try {
                action()
                val recipients = store.recipients(); val transfers = store.transfers()
                mutable.update { it.copy(recipients = recipients, transfers = transfers) }
            } catch (e: Exception) { notify((e as? IllegalArgumentException)?.message ?: "No se pudieron guardar los cambios. Inténtalo de nuevo.") }
            finally { mutable.update { it.copy(transferSaving = false) } }
        }
    }
    fun saveRecipient(id: String?, name: String, phone: String, favorite: Boolean) = transferMutation {
        store.saveRecipient(id, name, phone, favorite)
        notify("Destinatario guardado.")
    }
    fun deleteRecipient(id: String) = transferMutation { store.deleteRecipient(id) }
    fun transferResult(id: String, confirmed: Boolean) = transferMutation {
        store.transferStatus(id, if (confirmed) "USER_CONFIRMED" else "NOT_COMPLETED")
    }
    fun startTransfer(draft: TransferDraft, launch: (TransferDraft) -> Boolean) {
        val state = mutable.value
        if (!state.transferReady || state.transferSaving || state.managingLine || state.busy || (app.purchases.state.value.busy || app.bankRecharges.state.value.busy)) return
        if (state.lines.none { it.subscription == draft.subscription && it.cubacel && it.active }) {
            notify("Selecciona una SIM Cubacel activa para continuar."); return
        }
        mutable.update { it.copy(transferSaving = true) }
        viewModelScope.launch {
            try {
                val id = withContext(worker.asCoroutineDispatcher()) { store.prepareTransfer(draft) }
                val available = mutable.value.lines.any { it.subscription == draft.subscription && it.cubacel && it.active }
                val opened = available && launch(draft)
                val transfers = withContext(worker.asCoroutineDispatcher()) {
                    store.transferStatus(id, if (opened) "OPENED" else "OPEN_FAILED")
                    store.transfers()
                }
                mutable.update { it.copy(transfers = transfers) }
            } catch (_: Exception) { notify("No se pudo completar el registro local. Revisa el historial antes de reintentar; abrir el menú no confirma un envío.") }
            finally { mutable.update { it.copy(transferSaving = false) } }
        }
    }
    fun refreshLines() {
        val app = getApplication<Application>()
        val permitted = app.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val lines = try {
            if (!permitted) emptyList() else SimRepository(app).activeSubscriptions().map { sim ->
                val operator = app.getSystemService(TelephonyManager::class.java)?.createForSubscriptionId(sim.subscriptionId)?.simOperator
                Line(sim.subscriptionId, sim.simSlotIndex, sim.carrierName?.toString().orEmpty(), operator == "36801")
            }
        } catch (_: RuntimeException) { emptyList() }
        mutable.update { old -> old.copy(lines = lines, permission = permitted,
            selected = LineCatalog.selection(old.selected, LineCatalog.visible(lines, (old.entries.map { it.subscription } + old.transfers.map { it.subscription } + old.bankNotices.map { it.receivingSubscription }).toSet(), old.nicknames))) }
    }
    fun loadMobileUsage(days: Int = mutable.value.mobileUsage.days) {
        if (days !in listOf(7, 30) || mutable.value.mobileUsage.loading) return
        mutable.update { it.copy(mobileUsage = com.simple.mobile.data.statistics.MobileUsageState(loading = true, days = days)) }
        viewModelScope.launch {
            val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.simple.mobile.data.statistics.MobileUsage(app).read(days, System.currentTimeMillis())
            }
            mutable.update { it.copy(mobileUsage = result) }
        }
    }
    fun setRechargeDate(subscription: Int, date: java.time.LocalDate?) {
        if (!mutable.value.storageReady || mutable.value.managingLine) return
        mutable.update { it.copy(managingLine = true) }
        worker.execute {
            try {
                store.setRechargeDate(subscription, date)
                val dates = store.rechargeDates()
                mutable.update { it.copy(rechargeDates = dates) }
                app.reconcileReminders()
            } catch (_: Exception) { notify("No se pudo guardar la fecha. Inténtalo de nuevo.") }
            finally { mutable.update { it.copy(managingLine = false) } }
        }
    }
    fun select(subscription: Int) { mutable.update { it.copy(selected = subscription) } }
    fun dismissMessage() { mutable.update { it.copy(message = null) } }
    fun notify(text: String) { mutable.update { it.copy(message = text) } }
    private fun loadHistory() {
        worker.execute {
            try {
                val entries = store.history()
                val names = store.nicknames()
                val phones = store.linePhones()
                val dates = store.rechargeDates()
                app.publishReadings(entries)
                if (!disposed) mutable.update { it.copy(entries = entries, unreadableCount = store.unreadableCount, nicknames = names, linePhones = phones, rechargeDates = dates, loading = false, storageReady = true) }
            } catch (_: Exception) {
                if (!disposed) mutable.update { it.copy(loading = false, storageReady = false,
                    message = "No se pudo abrir el historial cifrado. Tus datos no se han borrado. Puedes reintentar desde Ajustes.") }
            }
        }
    }
    fun retryStorage() { loadHistory(); loadTransfers() }
    fun refreshSmsPermissions() {
        mutable.update { it.copy(
            smsReadAllowed = app.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED,
            smsReceiveAllowed = app.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED) }
    }
    fun setBankSms(enabled: Boolean) { app.smsImport.setBankEnabled(enabled) }
    fun enableSms() {
        refreshSmsPermissions()
        if (!mutable.value.permission || !mutable.value.smsReadAllowed || !mutable.value.smsReceiveAllowed) {
            notify("Faltan permisos para conectar la importación automática de SMS. Revísalos en Ajustes.")
            return
        }
        app.smsImport.setEnabled(true)
        syncCarrierSms()
    }
    fun syncCarrierSms(showMessage: Boolean = true, rescan: Boolean = false) {
        if (mutable.value.managingLine) return
        val token = app.smsImport.beginSync() ?: return
        worker.execute {
            var count = 0
            var error: String? = null
            try {
                count = com.simple.mobile.data.telephony.CarrierSmsSync.syncInbox(app, token, rescan)
                if (count > 0) app.refreshReadings()
                if (!disposed && showMessage && app.smsImport.isCurrent(token)) notify("$count registros nuevos añadidos." + if (app.smsImport.state.value.moreInbox) " Quedan mensajes nuevos: pulsa Sincronizar ahora para continuar." else " Revisión terminada.")
            } catch (_: SecurityException) { error = "Faltan permisos para sincronizar los SMS. Revisa los permisos en Android." }
            catch (_: Exception) { error = "No se pudo completar la sincronización. Las lecturas guardadas se conservan." }
            finally {
                if (error != null) try { app.refreshReadings() } catch (_: Exception) { /* Keep last visible readings on storage failure. */ }
                app.smsImport.finishSync(token, count, error)
                if (!disposed) {
                    refreshSmsPermissions()
                    if (error != null && showMessage && app.smsImport.isCurrent(token)) notify(error!!)
                }
            }
        }
    }
    val bankRecharges get() = app.bankRecharges
    val purchases get() = app.purchases
    fun purchasePlan(planId: String, subscription: Int) {
        val current = mutable.value
        if (current.busy || current.managingLine || current.transferSaving || app.bankRecharges.state.value.busy || !current.storageReady) return
        refreshLines()
        if (mutable.value.lines.none { it.subscription == subscription && it.active && it.cubacel }) {
            notify("La SIM elegida ya no está disponible. Vuelve a elegir la línea."); return
        }
        app.purchases.confirm(planId, subscription)
    }
    fun check(query: CubacelQuery) = startQueries(listOf(query))
    fun refreshAll() = startQueries(RefreshSequence.ALL)
    fun refreshNeeded() = startQueries(RefreshPolicy.needed(mutable.value.entries, mutable.value.selected, System.currentTimeMillis()))
    fun retryQueries() = startQueries(mutable.value.pendingQueries[mutable.value.selected].orEmpty())
    fun stopRefresh() { mutable.update { if (it.busy) it.copy(stopRequested = true) else it } }

    private fun startQueries(queries: List<CubacelQuery>) {
        val now = mutable.value
        if (now.busy || now.managingLine || (app.purchases.state.value.busy || app.bankRecharges.state.value.busy)) return
        if (queries.isEmpty()) { notify("No hay consultas pendientes. Las lecturas tienen menos de 15 minutos; puedes forzar Actualizar todo."); return }
        if (!now.storageReady) { notify("El almacenamiento no está disponible. Reintenta desde Ajustes antes de consultar."); return }
        val line = now.lines.firstOrNull { it.subscription == now.selected }
        if (line == null) { notify("Conecta y selecciona una SIM activa."); return }
        if (!line.cubacel) { notify("Selecciona tu SIM Cubacel. No enviaremos códigos a otro operador."); return }
        mutable.update { it.copy(busy = true, queryTotal = queries.size, queryNumber = 0,
            querySubscription = line.subscription, stopRequested = false,
            pendingQueries = it.pendingQueries + (line.subscription to (it.pendingQueries[line.subscription].orEmpty() + queries).distinct())) }
        viewModelScope.launch {
            var completed = 0
            try {
                RefreshSequence.run(queries, { mutable.value.stopRequested }) { query ->
                    mutable.update { it.copy(activeQuery = query, queryNumber = completed + 1) }
                    val response = suspendCancellableCoroutine<String> { continuation ->
                        val future = app.dispatcher().queryText(line.subscription, query)
                        request = future
                        continuation.invokeOnCancellation { future.cancel(false) }
                        future.whenComplete { value, error ->
                            if (error != null) continuation.resumeWithException(error)
                            else continuation.resume(value)
                        }
                    }
                    val receivedAt = System.currentTimeMillis()
                    withContext(worker.asCoroutineDispatcher()) {
                        val parsed = parser.parse(query, response, receivedAt)
                        store.save(line.subscription, parsed)
                        app.refreshReadings()
                    }
                    completed++
                    mutable.update { old -> old.copy(pendingQueries = old.pendingQueries +
                        (line.subscription to RefreshPolicy.remaining(old.pendingQueries[line.subscription].orEmpty(), setOf(query)))) }
                }
                finish(if (mutable.value.stopRequested) "Actualización detenida: $completed de ${queries.size} consultas guardadas."
                    else "Información actualizada: $completed de ${queries.size} consultas guardadas en tu móvil.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                var cause: Throwable = failure
                while (cause.cause != null) cause = cause.cause!!
                val message = if (cause is CubacelParser.ParseException)
                    "El operador respondió con un formato no compatible o ambiguo. Conservamos la última lectura."
                else when ((cause as? UssdDispatcher.QueryException)?.failure) {
                    UssdDispatcher.Failure.TIMEOUT, UssdDispatcher.Failure.BUSY -> "El operador no respondió o hay una sesión pendiente. Espera antes de reintentar."
                    UssdDispatcher.Failure.PERMISSION -> "Permite acceso al teléfono y llamadas en Ajustes para consultar."
                    UssdDispatcher.Failure.WRONG_CARRIER -> "La SIM seleccionada no es Cubacel."
                    null -> "No se pudo guardar la lectura de forma segura. El último registro se conserva."
                    else -> "No se pudo consultar. Comprueba la cobertura y vuelve a intentarlo."
                }
                finish(if (queries.size > 1) "$message Actualización interrumpida: $completed de ${queries.size} consultas guardadas." else message)
            }
        }
    }
    private fun finish(message: String) {
        if (!disposed) mutable.update { it.copy(busy = false, activeQuery = null, stopRequested = false, message = message) }
        request = null
    }
    fun refreshReminderSettings() {
        mutable.update { it.copy(remindersEnabled = app.reminders.enabled, notificationsAllowed = app.reminders.allowed()) }
        worker.execute { app.reconcileReminders() }
    }
    fun setReminders(enabled: Boolean) {
        app.reminders.setEnabled(enabled)
        refreshReminderSettings()
    }
    fun disableSms() {
        app.smsImport.setEnabled(false)
        notify("Importación automática de SMS desactivada. Tus lecturas guardadas se conservan.")
    }
    fun renameLine(subscription: Int, nickname: String, phone: String) {
        if (mutable.value.managingLine || !mutable.value.storageReady) return
        mutable.update { it.copy(managingLine = true) }
        worker.execute {
            try {
                store.saveLineProfile(subscription, nickname, phone)
                val names = store.nicknames()
                val phones = store.linePhones()
                val dates = store.rechargeDates()
                if (!disposed) mutable.update { it.copy(nicknames = names, linePhones = phones, rechargeDates = dates, message = "Datos de la línea guardados.") }
            } catch (_: Exception) { if (!disposed) notify("No se pudo guardar el nombre de la línea.") }
            finally { if (!disposed) mutable.update { it.copy(managingLine = false) } }
        }
    }
    fun clearHistory(subscription: Int? = null) {
        if (mutable.value.busy || mutable.value.managingLine || mutable.value.transferSaving || (app.purchases.state.value.busy || app.bankRecharges.state.value.busy)) { notify("Espera a que termine la consulta antes de borrar datos."); return }
        mutable.update { it.copy(managingLine = true) }
        app.smsImport.setEnabled(false)
        worker.execute {
            try {
                app.purchases.clearForLine(subscription)
                app.bankRecharges.clearForLine(subscription)
                com.simple.mobile.data.recharge.BankPinVault.clear(app, subscription)
                app.bankNoticeStore.clear(subscription)
                app.refreshBankNotices()
                store.clear(subscription)
                val recipients = store.recipients(); val transfers = store.transfers()
                mutable.update { it.copy(recipients = recipients, transfers = transfers) }
                app.smsImport.clearSummary()
                app.refreshReadings()
                val names = store.nicknames()
                val phones = store.linePhones()
                val dates = store.rechargeDates()
                if (!disposed) mutable.update { it.copy(nicknames = names, linePhones = phones, rechargeDates = dates, storageReady = true,
                    message = if (subscription == null) "Datos locales eliminados. Importación SMS desactivada."
                        else "Datos de esta línea eliminados. Las demás líneas se conservan. Importación SMS desactivada.") }
            } catch (_: Exception) { if (!disposed) notify("No se pudieron borrar los datos. Inténtalo de nuevo.") }
            finally { if (!disposed) mutable.update { it.copy(managingLine = false) } }
        }
    }
    override fun onCleared() {
        disposed = true
        request?.cancel(false)

    }
}
