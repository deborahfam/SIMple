package com.simple.mobile.presentation.recharge

import android.app.Activity
import android.app.KeyguardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.simple.mobile.data.recharge.BankPinVault
import com.simple.mobile.domain.recharge.MetropolitanoProtocol

/** Explicit opt-in to automatic local PIN loading; never sends a bank request. */
@Composable internal fun SavedBankPin(subscription: Int, pin: String, onPin: (String) -> Unit) {
    if (subscription < 0) return
    val context = LocalContext.current
    val vault = remember(subscription) { BankPinVault(context, subscription) }
    val currentOnPin by rememberUpdatedState(onPin)
    var saved by remember(vault) { mutableStateOf(vault.saved) }
    var automatic by remember(vault) { mutableStateOf(vault.automatic) }
    var message by remember(vault) { mutableStateOf<String?>(null) }
    var migrating by remember(vault) { mutableStateOf(false) }
    LaunchedEffect(vault) {
        if (vault.automatic) try {
            val value = vault.unlock()
            try { currentOnPin(String(value)) } finally { value.fill('\u0000') }
        } catch (_: Exception) { message = "Vuelve a guardar el PIN." }
    }
    // Existing v1 secrets need one final device authorization before opt-in migration.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (migrating && result.resultCode == Activity.RESULT_OK) try {
            val value = vault.unlock()
            try { currentOnPin(String(value)); vault.save(value) } finally { value.fill('\u0000') }
            automatic = true
        } catch (_: Exception) { message = "Elimina el PIN y guárdalo de nuevo." }
        migrating = false
    }
    Column(Modifier.fillMaxWidth()) {
        if (saved) {
            if (automatic) Text("PIN guardado · uso automático", style = MaterialTheme.typography.bodySmall)
            else TextButton(onClick = {
                try {
                    @Suppress("DEPRECATION")
                    val intent = context.getSystemService(KeyguardManager::class.java)
                        .createConfirmDeviceCredentialIntent("PIN guardado", "Autoriza el uso automático a partir de ahora") ?: error("Unavailable")
                    migrating = true; launcher.launch(intent)
                } catch (_: Exception) { migrating = false; message = "Elimina el PIN y guárdalo de nuevo." }
            }, enabled = !migrating) { Text("Activar uso automático") }
            TextButton(onClick = {
                try { vault.forget(); saved = false; automatic = false; currentOnPin(""); message = null }
                catch (_: Exception) { message = "No se pudo eliminar. Reintenta." }
            }, enabled = !migrating) { Text("Eliminar PIN") }
        } else {
            TextButton(onClick = {
                try { vault.save(pin.toCharArray()); saved = true; automatic = true; message = null }
                catch (_: Exception) { message = "No se pudo guardar. Reintenta." }
            }, enabled = MetropolitanoProtocol.validPin(pin.toCharArray())) { Text("Guardar PIN") }
        }
        if (!automatic) Text("Se usará sin pedirlo de nuevo en este teléfono.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
