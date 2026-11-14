package com.simple.mobile.presentation

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.simple.mobile.SimpleApplication
import com.simple.mobile.data.SimRepository
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

data class Line(val subscription: Int, val slot: Int, val carrier: String, val cubacel: Boolean)
data class DashboardState(
    val lines: List<Line> = emptyList(), val selected: Int = -1,
    val entries: List<EncryptedReadingStore.Entry> = emptyList(),
    val loading: Boolean = true, val busy: Boolean = false,
    val message: String? = null, val permission: Boolean = false,
    val storageReady: Boolean = false,
    val activeQuery: CubacelQuery? = null, val queryNumber: Int = 0, val queryTotal: Int = 0,
    val querySubscription: Int = -1, val stopRequested: Boolean = false,
    val remindersEnabled: Boolean = false, val notificationsAllowed: Boolean = false
 ) {
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
        viewModelScope.launch { app.readings.collect { values -> mutable.update { it.copy(entries = values) } } }
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
            selected = if (lines.any { it.subscription == old.selected }) old.selected else lines.firstOrNull { it.cubacel }?.subscription ?: lines.firstOrNull()?.subscription ?: -1) }
    }
    fun select(subscription: Int) { mutable.update { it.copy(selected = subscription) } }
    fun dismissMessage() { mutable.update { it.copy(message = null) } }
    fun notify(text: String) { mutable.update { it.copy(message = text) } }
    private fun loadHistory() {
        worker.execute {
            try {
                val entries = store.history()
                app.publishReadings(entries)
                if (!disposed) mutable.update { it.copy(entries = entries, loading = false, storageReady = true) }
            } catch (_: Exception) {
                if (!disposed) mutable.update { it.copy(loading = false, storageReady = false,
                    message = "No se pudo abrir el historial cifrado. Tus datos no se han borrado. Puedes reintentar desde Ajustes.") }
            }
        }
    }
    fun retryStorage() { loadHistory() }
    fun syncCarrierSms(showMessage: Boolean = true) {
        worker.execute {
            try {
                val count = com.simple.mobile.data.telephony.CarrierSmsSync.syncInbox(app)
                app.refreshReadings()
                if (!disposed && showMessage) notify("Sincronización terminada: $count lecturas reconocidas. Los anuncios no se importan.")
            } catch (_: SecurityException) { if (!disposed) notify("Permite acceso a SMS para sincronizar los mensajes de Cubacel.") }
            catch (_: Exception) { if (!disposed) notify("No se pudieron sincronizar los SMS. Los datos guardados se conservan.") }
        }
    }
    fun check(query: CubacelQuery) = startQueries(listOf(query))
    fun refreshAll() = startQueries(RefreshSequence.ALL)
    fun stopRefresh() { mutable.update { if (it.busy) it.copy(stopRequested = true) else it } }

    private fun startQueries(queries: List<CubacelQuery>) {
        val now = mutable.value
        if (now.busy) return
        if (!now.storageReady) { notify("El almacenamiento no está disponible. Reintenta desde Ajustes antes de consultar."); return }
        val line = now.lines.firstOrNull { it.subscription == now.selected }
        if (line == null) { notify("Conecta y selecciona una SIM activa."); return }
        if (!line.cubacel) { notify("Selecciona tu SIM Cubacel. No enviaremos códigos a otro operador."); return }
        mutable.update { it.copy(busy = true, queryTotal = queries.size, queryNumber = 0,
            querySubscription = line.subscription, stopRequested = false) }
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
        app.getSharedPreferences("features", android.content.Context.MODE_PRIVATE).edit().putBoolean("carrier_sms", false).apply()
        notify("Importación automática de SMS desactivada. Tus lecturas guardadas se conservan.")
    }
    fun clearHistory() {
        app.getSharedPreferences("features", android.content.Context.MODE_PRIVATE).edit().putBoolean("carrier_sms", false).apply()
        worker.execute {
            try { store.clear(); app.publishReadings(emptyList()); if (!disposed) mutable.update { it.copy(entries = emptyList(), storageReady = true, message = "Historial eliminado de este dispositivo.") } }
            catch (_: Exception) { finish("No se pudo borrar el historial. Inténtalo de nuevo.") }
        }
    }
    override fun onCleared() {
        disposed = true
        request?.cancel(false)

    }
}
