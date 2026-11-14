package com.simple.mobile

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
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
    private var pendingQuery: String? = null
    private var pendingSubscription: Int = -1
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
        model = ViewModelProvider(this)[DashboardViewModel::class.java]
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
            Dashboard(model,
                onConnect = { permissions.launch(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.CALL_PHONE)) },
                onQuery = { query -> requestQuery(query.name) },
                onRefreshAll = { requestQuery("ALL") },
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
                onSettings = { open(Intent(Settings.ACTION_WIRELESS_SETTINGS)) },
                onAppSettings = { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) },
                onDial = { code -> open(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", code, null))) },
                onRechargeSite = { open(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.etecsa.cu/es/movil/necesitas-saldo"))) })
        }
    }
    override fun onResume() {
        super.onResume()
        if (::model.isInitialized) {
            model.refreshLines()
            resumePendingQuery()
            model.refreshReminderSettings()
            model.refreshSmsPermissions()
            selectReminderLine(intent)
            if ((application as SimpleApplication).smsImport.state.value.enabled &&
                checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) model.syncCarrierSms(false)
        }
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
        if (query == "ALL") model.refreshAll() else CubacelQuery.values().firstOrNull { it.name == query }?.let { model.check(it) }
        return true
    }
    override fun onSaveInstanceState(outState: Bundle) {
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
