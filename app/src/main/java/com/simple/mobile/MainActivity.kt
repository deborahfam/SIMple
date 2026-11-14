package com.simple.mobile

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import com.simple.mobile.domain.carrier.CubacelQuery
import com.simple.mobile.presentation.Dashboard
import com.simple.mobile.presentation.DashboardViewModel

class MainActivity : ComponentActivity() {
    private lateinit var model: DashboardViewModel
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        model.refreshLines()
        model.notify(if (it.values.all { granted -> granted }) "Permisos listos. Ya puedes consultar tu SIM."
            else "Puedes ver tus lecturas guardadas. Para consultar, permite teléfono y llamadas en Ajustes.")
    }
    private val smsPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            getSharedPreferences("features", MODE_PRIVATE).edit().putBoolean("carrier_sms", true).apply()
            model.refreshLines()
            model.syncCarrierSms()
        } else model.notify("Android no permitió leer los SMS. Las consultas USSD siguen disponibles. Puedes revisar los permisos en Ajustes.")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[DashboardViewModel::class.java]
        setContent {
            Dashboard(model,
                onConnect = { permissions.launch(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.CALL_PHONE)) },
                onQuery = { query ->
                    if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED ||
                        checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED)
                        permissions.launch(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.CALL_PHONE))
                    else model.check(query)
                },
                onRefreshAll = {
                    if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED ||
                        checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED)
                        permissions.launch(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.CALL_PHONE))
                    else model.refreshAll()
                },
                onSyncSms = { smsPermissions.launch(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)) },
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
            if (getSharedPreferences("features", MODE_PRIVATE).getBoolean("carrier_sms", false) &&
                checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) model.syncCarrierSms(false)
        }
    }
    private fun open(intent: Intent) {
        try { startActivity(intent) } catch (_: ActivityNotFoundException) { model.notify("No hay una aplicación disponible para abrir esta opción.") }
    }
}
