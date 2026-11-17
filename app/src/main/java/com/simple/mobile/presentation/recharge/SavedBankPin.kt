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

/** Saving/unlocking only fills the form: never authenticates at the bank or sends money. */
@Composable internal fun SavedBankPin(subscription: Int, pin: String, onPin: (String) -> Unit) {
    if (subscription < 0) return
    val context = LocalContext.current
    val vault = remember(subscription) { BankPinVault(context, subscription) }
    val keyguard = context.getSystemService(KeyguardManager::class.java)
    var saved by remember(vault) { mutableStateOf(vault.saved) }
    var pending by remember(vault) { mutableStateOf<CharArray?>(null) }
    var action by remember(vault) { mutableStateOf<String?>(null) }
    var message by remember(vault) { mutableStateOf<String?>(null) }
    DisposableEffect(vault) { onDispose { pending?.fill('\u0000'); pending = null } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val operation = action
        action = null
        try {
            if (result.resultCode == Activity.RESULT_OK && operation != null) {
                if (operation == "save") {
                    val value = pending ?: error("Expired")
                    vault.save(value)
                    saved = vault.saved
                    message = "PIN guardado para Metropolitano en esta SIM."
                } else {
                    val value = vault.unlock()
                    try { onPin(String(value)) } finally { value.fill('\u0000') }
                    message = "PIN listo. Pulsa Autenticar para continuar."
                }
            } else message = "Operación cancelada. Puedes escribir tu PIN."
        } catch (_: Exception) {
            message = "No se pudo acceder al PIN. Puedes eliminarlo y guardarlo de nuevo."
        } finally { pending?.fill('\u0000'); pending = null }
    }
    fun authorize(save: Boolean) {
        try {
            check(keyguard?.isDeviceSecure == true)
            vault.prepare()
            @Suppress("DEPRECATION")
            val intent = keyguard.createConfirmDeviceCredentialIntent("PIN de Metropolitano", if (save) "Confirma para guardar el PIN en este teléfono" else "Confirma para usar el PIN guardado") ?: error("Unavailable")
            if (save) { pending = pin.toCharArray(); onPin("") }
            action = if (save) "save" else "use"
            launcher.launch(intent)
        } catch (_: Exception) {
            pending?.fill('\u0000'); pending = null; action = null
            message = "Configura un bloqueo seguro del teléfono para guardar el PIN."
        }
    }
    Column(Modifier.fillMaxWidth()) {
        if (saved) {
            OutlinedButton(onClick = { authorize(false) }, enabled = action == null) { Text("Usar PIN guardado") }
            TextButton(onClick = {
                try { vault.forget(); saved = false; onPin(""); message = "PIN eliminado." }
                catch (_: Exception) { message = "No se pudo eliminar el PIN. Reintenta." }
            }, enabled = action == null) { Text("Eliminar PIN guardado") }
        } else {
            OutlinedButton(onClick = { authorize(true) }, enabled = action == null && MetropolitanoProtocol.validPin(pin.toCharArray())) {
                Text("Guardar PIN en este teléfono")
            }
        }
        Text("Opcional · cifrado y protegido con el bloqueo del teléfono.", style = MaterialTheme.typography.bodySmall)
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
