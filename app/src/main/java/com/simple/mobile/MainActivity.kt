package com.simple.mobile

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.provider.ContactsContract
import com.simple.mobile.domain.transfer.TransferPolicy
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.simple.mobile.domain.carrier.CubacelQuery
import com.simple.mobile.presentation.Dashboard
import com.simple.mobile.presentation.DashboardViewModel

class MainActivity : ComponentActivity() {
    private lateinit var demo: com.simple.mobile.presentation.recharge.RechargeDemoViewModel
    private var pendingDemoPin: Pair<Boolean, String>? = null
    private val demoCredential = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val pending = pendingDemoPin
        pendingDemoPin = null
        if (result.resultCode == RESULT_OK && pending != null) demo.pinOperation(pending.first, pending.second)
    }
    @Suppress("DEPRECATION")
    private fun authorizeDemoPin(save: Boolean, attemptId: String) {
        if (pendingDemoPin != null) return
        val lock = getSystemService(android.app.KeyguardManager::class.java)
        if (!lock.isDeviceSecure) {
            demo.error("Configura un bloqueo de pantalla en Android para guardar el PIN ficticio. También puedes escribir 1234 sin guardarlo.")
            return
        }
        pendingDemoPin = save to attemptId
        lifecycleScope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { demo.vault.prepareKey() }
                if (!demo.state.value.open || demo.state.value.attempt?.id != attemptId) { pendingDemoPin = null; return@launch }
                val intent = lock.createConfirmDeviceCredentialIntent("PIN ficticio de SIMple", "Desbloquea Android para ${if (save) "guardar" else "usar"} el PIN de prueba. No es el PIN bancario.")
                if (intent == null) { pendingDemoPin = null; demo.error("Android no ofrece desbloqueo seguro en este dispositivo.") }
                else demoCredential.launch(intent)
            } catch (_: Exception) { pendingDemoPin = null; demo.error("No se pudo abrir el almacenamiento seguro. Usa Olvidar PIN ficticio si cambiaste el bloqueo del dispositivo.") }
        }
    }
    private var externalSubscription: Int? = null
    private var externalLeft = false
    private val contactPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) result.data?.data?.let { uri ->
            try {
                contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) model.pickedContact(cursor.getString(0).orEmpty(), cursor.getString(1).orEmpty())
                }
            } catch (_: Exception) { model.notify("No se pudo leer ese contacto. Puedes escribir el número manualmente.") }
        }
    }
    private var pendingQuery: String? = null
    private var pendingSubscription: Int = -1
    private lateinit var updates: com.simple.mobile.data.updates.AppUpdates
    private lateinit var model: DashboardViewModel
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        model.refreshLines()
        if (resumePendingQuery()) return@registerForActivityResult
        model.notify(if (it.values.all { granted -> granted }) "Permisos listos. Ya puedes consultar tu SIM."
            else "Puedes ver tus lecturas guardadas. Para consultar, permite teléfono y llamadas en Ajustes.")
    }
    private val smsPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        model.refreshLines()
        model.refreshSmsPermissions()
        model.enableSms()
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        model.setReminders(granted)
        if (!granted) model.notify("Los recordatorios están desactivados. Puedes permitir notificaciones desde Ajustes.")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updates = ViewModelProvider(this)[com.simple.mobile.data.updates.AppUpdates::class.java]
        model = ViewModelProvider(this)[DashboardViewModel::class.java]
        demo = ViewModelProvider(this)[com.simple.mobile.presentation.recharge.RechargeDemoViewModel::class.java]
        externalSubscription = savedInstanceState?.getInt("external_subscription", -1)?.takeIf { it >= 0 }
        externalLeft = savedInstanceState?.getBoolean("external_left") ?: false
        pendingQuery = savedInstanceState?.getString("pending_query")
        pendingSubscription = savedInstanceState?.getInt("pending_subscription", -1) ?: -1
        lifecycleScope.launch {
            model.state.collect { if (it.storageReady && !it.busy && !it.managingLine) resumePendingQuery() }
        }
        setContent {
            val dark = androidx.compose.foundation.isSystemInDarkTheme()
            androidx.compose.runtime.SideEffect {
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            Dashboard(model, updates, onInstallUpdate = { installUpdate() },
                onConnect = { permissions.launch(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.CALL_PHONE)) },
                onQuery = { query -> requestQuery(query.name) },
                onRefreshAll = { requestQuery("ALL") },
                onRefreshNeeded = { requestQuery("SMART") },
                onRetryQueries = { requestQuery("RETRY") },
                demo = demo, onDemoPin = ::authorizeDemoPin,
                onRechargeDemo = {
                    val state = model.state.value
                    demo.open(state.selected, state.displayLines.firstOrNull { it.subscription == state.selected }?.label ?: "SIM de prueba")
                },
                onReminders = { enabled ->
                    if (!enabled) model.setReminders(false)
                    else if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else model.setReminders(true)
                },
                onNotificationSettings = { open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) },
                onSmsEnabled = { enabled ->
                    if (!enabled) model.disableSms()
                    else smsPermissions.launch(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
                },
                onSyncSms = { model.syncCarrierSms() },
                onRescanSms = { model.syncCarrierSms(rescan = true) },
                onSettings = { open(Intent(Settings.ACTION_WIRELESS_SETTINGS)) },
                onAppSettings = { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) },
                onPickContact = {
                    try { contactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }
                    catch (_: ActivityNotFoundException) { model.notify("No hay un selector de contactos disponible. Escribe el número manualmente.") }
                },
                onTransfer = { draft -> openCarrier(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", TransferPolicy.MENU, null)), draft.subscription) })
        }
    }
    override fun onResume() {
        super.onResume()
        if (::model.isInitialized) {
            model.refreshLines()
            if (externalLeft) {
                externalSubscription?.let { model.offerRefresh(it) }
                externalSubscription = null; externalLeft = false
            }
            resumePendingQuery()
            updates.checkOnOpen()
            model.refreshReminderSettings()
            model.refreshSmsPermissions()
            selectReminderLine(intent)
            if ((application as SimpleApplication).smsImport.state.value.enabled &&
                checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) model.syncCarrierSms(false)
        }
    }
    override fun onPause() {
        if (externalSubscription != null) externalLeft = true
        super.onPause()
    }
    private fun openCarrier(intent: Intent, subscription: Int): Boolean {
        return try {
            startActivity(intent)
            externalSubscription = subscription.takeIf { it >= 0 }
            externalLeft = false
            true
        } catch (_: ActivityNotFoundException) {
            model.notify("No hay una aplicación disponible para abrir esta opción."); false
        } catch (_: SecurityException) {
            model.notify("Android no permitió abrir esta opción."); false
        }
    }
    private fun installUpdate() {
        if (!updates.state.value.ready) return
        if (!packageManager.canRequestPackageInstalls()) {
            open(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            model.notify("Permite instalar desde SIMple y vuelve a pulsar Instalar actualización.")
            return
        }
        try {
            updates.validate(updates.state.value.release!!.code)
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.updates", updates.apk)
            open(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (e: Exception) { model.notify(e.message ?: "No se pudo abrir el instalador.") }
    }
    private fun requestQuery(query: String) {
        pendingQuery = query
        pendingSubscription = model.state.value.selected
        if (!resumePendingQuery()) permissions.launch(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.CALL_PHONE))
    }
    private fun resumePendingQuery(): Boolean {
        val query = pendingQuery ?: return false
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) return false
        if (!model.state.value.storageReady || model.state.value.busy || model.state.value.managingLine) return false
        pendingQuery = null // Consume before dispatch, including callback/onResume races.
        if (model.state.value.lines.none { it.subscription == pendingSubscription && it.cubacel }) {
            model.notify("La SIM de la consulta ya no está disponible. Selecciona una línea y vuelve a consultar.")
            return true
        }
        model.select(pendingSubscription)
        when (query) {
            "ALL" -> model.refreshAll()
            "SMART" -> model.refreshNeeded()
            "RETRY" -> model.retryQueries()
            else -> CubacelQuery.values().firstOrNull { it.name == query }?.let { model.check(it) }
        }
        return true
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("external_subscription", externalSubscription ?: -1)
        outState.putBoolean("external_left", externalLeft)
        outState.putString("pending_query", pendingQuery)
        outState.putInt("pending_subscription", pendingSubscription)
        super.onSaveInstanceState(outState)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        model.refreshLines()
        selectReminderLine(intent)
    }
    private fun selectReminderLine(intent: Intent) {
        val subscription = intent.getIntExtra("reminder_subscription", -1)
        if (model.state.value.lines.any { it.subscription == subscription }) model.select(subscription)
        intent.removeExtra("reminder_subscription")
    }
    private fun open(intent: Intent) {
        try { startActivity(intent) } catch (_: ActivityNotFoundException) { model.notify("No hay una aplicación disponible para abrir esta opción.") }
    }
}
