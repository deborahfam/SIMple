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

/** Every save/load explicitly prompts for the device credential; no automatic decryption. */
@Composable internal fun SavedBankPin(subscription: Int, pin: String, onPin: (String) -> Unit) {
    if (subscription < 0) return
    val context = LocalContext.current
    val vault = remember(subscription) { runCatching { BankPinVault(context, subscription) }.getOrNull() }
    if (vault == null) {
        Text("El almacenamiento del PIN no está disponible. Puedes escribirlo sin guardarlo; cierra y vuelve a abrir esta pantalla para reintentar.", style = MaterialTheme.typography.bodySmall)
        return
    }
    val currentOnPin by rememberUpdatedState(onPin)
    var saved by remember(vault) { mutableStateOf(vault.saved) }
    var legacy by remember(vault) { mutableStateOf(vault.needsReplacement) }
    var message by remember(vault) { mutableStateOf<String?>(null) }
    // Intentionally not saveable: rotation/process death cancels, never serializes a bank PIN.
    var pendingPin by remember(vault) { mutableStateOf<CharArray?>(null) }
    var pending by remember(vault) { mutableStateOf(false) }
    DisposableEffect(vault) { onDispose { pendingPin?.fill('\u0000'); pendingPin = null } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val value = pendingPin
        pendingPin = null
        try {
            if (pending && result.resultCode == Activity.RESULT_OK) {
                if (value != null) {
                    vault.save(value)
                    saved = true; legacy = false
                    message = "PIN protegido con el bloqueo del dispositivo."
                } else {
                    val loaded = vault.unlock()
                    try { currentOnPin(String(loaded)) } finally { loaded.fill('\u0000') }
                    message = null
                }
            } else message = "Operación cancelada. Puedes escribir el PIN."
        } catch (_: Exception) {
            message = "No se pudo acceder al PIN. Reintenta o elimínalo y guárdalo de nuevo."
        } finally { value?.fill('\u0000'); pending = false }
    }
    fun authenticate(save: Boolean) {
        try {
            val keyguard = context.getSystemService(KeyguardManager::class.java)
            check(keyguard.isDeviceSecure)
            if (save) vault.prepare()
            @Suppress("DEPRECATION")
            val intent = keyguard.createConfirmDeviceCredentialIntent("PIN bancario guardado", "Confirma el bloqueo del dispositivo para continuar") ?: error("Unavailable")
            pendingPin = if (save) pin.toCharArray() else null
            pending = true
            currentOnPin("")
            launcher.launch(intent)
        } catch (_: Exception) {
            pendingPin?.fill('\u0000'); pendingPin = null; pending = false
            message = "Configura un PIN, patrón o contraseña de bloqueo en Android para guardar el PIN bancario."
        }
    }
    Column(Modifier.fillMaxWidth()) {
        if (legacy) Text("El PIN anterior debe reemplazarse para protegerlo con el bloqueo de Android. Escríbelo y guárdalo de nuevo.", style = MaterialTheme.typography.bodySmall)
        if (saved) TextButton(onClick = { authenticate(false) }, enabled = !pending) { Text("Desbloquear PIN guardado") }
        else TextButton(onClick = { authenticate(true) }, enabled = !pending && MetropolitanoProtocol.validPin(pin.toCharArray())) { Text("Guardar PIN protegido") }
        if (saved || legacy) TextButton(onClick = {
            try { vault.forget(); saved = false; legacy = false; currentOnPin(""); message = null }
            catch (_: Exception) { message = "No se pudo eliminar. Reintenta." }
        }, enabled = !pending) { Text("Eliminar PIN") }
        Text("Android pedirá confirmar tu bloqueo cada vez que guardes o cargues el PIN. No se envía ninguna operación al desbloquearlo.", style = MaterialTheme.typography.bodySmall)
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
