package com.simple.mobile.presentation.recharge

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simple.mobile.presentation.FlowProgress
import com.simple.mobile.domain.recharge.RechargeSimulation as Demo

@Composable fun RechargeScreen(model: RechargeDemoViewModel, onPin: (Boolean, String) -> Unit,
    bankNotices: @Composable (Int) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    if (!state.open) return
    var bank by remember { mutableStateOf(Demo.Bank.METRO) }
    var phone by remember { mutableStateOf("55500000") }
    var cup by remember { mutableStateOf("") }
    var pin by remember(state.attempt?.id, state.attempt?.phase) { mutableStateOf("") }
    val scroll = rememberScrollState()
    val a = state.attempt
    val step = when (a?.phase) {
        null -> 0
        Demo.Phase.REVIEW, Demo.Phase.AUTH_PENDING -> 1
        else -> 2
    }
    Dialog(onDismissRequest = model::close,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn,
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val focus = LocalFocusManager.current
        LaunchedEffect(a?.id, a?.phase) { scroll.scrollTo(0); focus.clearFocus() }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Recargar", style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f).semantics { heading() })
                        IconButton(onClick = model::close) { Icon(Icons.Outlined.Close, "Cerrar recarga") }
                    }
                }, bottomBar = {
                    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            val button = Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(min = 52.dp)
                            when {
                                a == null -> Button(onClick = { focus.clearFocus(); model.prepare(bank, phone, cup) }, modifier = button,
                                    enabled = !state.busy && state.subscription >= 0 && Demo.amount(cup) != null && Regex("5[0-9]{7}").matches(phone)) { Text("Continuar a revisión") }
                                a.phase == Demo.Phase.REVIEW -> Button(onClick = {
                                    focus.clearFocus()
                                    val value = pin; pin = ""; model.authenticate(value)
                                }, modifier = button, enabled = !state.busy && pin.length == 4) { Text("Confirmar prueba con PIN") }
                                else -> OutlinedButton(onClick = model::close, modifier = button) { Text("Volver a mi línea") }
                            }
                        }
                    }
                }) { padding ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(scroll).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        FlowProgress(step, listOf("Prepara tu recarga", "Revisa tu recarga", "Tu resultado"))
                        Text("SIMULACIÓN · No mueve dinero", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text("No conecta con Transfermóvil ni con un banco. No envía USSD ni SMS. Usa solamente el PIN ficticio 1234.")
                        if (a == null) Text("Línea: ${state.line}", style = MaterialTheme.typography.bodySmall)
                        if (a == null) {
                            Text("Banco de prueba")
                            Demo.Bank.entries.forEach { choice ->
                                FilterChip(selected = bank == choice, onClick = { bank = choice }, label = { Text(choice.label) }, enabled = !state.busy)
                            }
                            OutlinedTextField(phone, { phone = it.filter(Char::isDigit).take(8) }, label = { Text("Número ficticio") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true)
                            OutlinedTextField(cup, { cup = it.take(12) }, label = { Text("Importe de prueba · CUP") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                            if (state.subscription < 0) Text("Selecciona una SIM para iniciar la prueba.")
                        } else {
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("${a.cup} CUP", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                                    RechargeSummaryRow("Móvil ficticio", a.phone)
                                    RechargeSummaryRow("Banco de prueba", a.bank.label)
                                    RechargeSummaryRow("Línea", a.line)
                                }
                            }
                            Text(when (a.phase) {
                                Demo.Phase.REVIEW -> "Revisa la línea, el número ficticio y el importe."
                                Demo.Phase.AUTH_PENDING -> "Esperando autenticación simulada. Elige una respuesta abajo."
                                Demo.Phase.RESULT_PENDING -> "Autenticación simulada correcta. Recarga simulada enviada una sola vez."
                                Demo.Phase.SUCCESS -> "Prueba completada. No se ha recargado saldo real."
                                Demo.Phase.AUTH_REJECTED -> "Autenticación simulada rechazada. No se simuló ningún envío."
                                Demo.Phase.REJECTED -> "Recarga simulada rechazada."
                                Demo.Phase.EXPIRED -> "Sesión de prueba vencida. No se simuló ningún envío."
                                Demo.Phase.UNCERTAIN -> "Sin confirmación. No se repetirá la operación. Puedes simular una respuesta tardía durante los 5 minutos de esta prueba."
                            })
                            Text("Solicitudes reales: 0 · Envíos simulados: ${a.simulatedSends}")
                            if (a.phase == Demo.Phase.REVIEW) {
                                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Outlined.Lock, null)
                                            Text("PIN de prueba", style = MaterialTheme.typography.titleMedium)
                                        }
                                        Text(if (state.savedPin) "PIN ficticio guardado en este dispositivo" else "Sin PIN guardado",
                                            style = MaterialTheme.typography.bodySmall)
                                        OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(4) },
                                            label = { Text("PIN ficticio: 1234") }, visualTransformation = PasswordVisualTransformation(),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                            enabled = !state.busy, singleLine = true, modifier = Modifier.fillMaxWidth())
                                        if (state.savedPin) {
                                            OutlinedButton(onClick = { focus.clearFocus(); pin = ""; onPin(false, a.id) }, enabled = !state.busy,
                                                modifier = Modifier.fillMaxWidth()) { Text("Usar PIN guardado") }
                                            TextButton(onClick = model::forgetPin, enabled = !state.busy) { Text("Eliminar PIN guardado") }
                                        } else {
                                            OutlinedButton(onClick = { focus.clearFocus(); pin = ""; onPin(true, a.id) }, enabled = !state.busy,
                                                modifier = Modifier.fillMaxWidth()) { Text("Guardar PIN ficticio 1234") }
                                        }
                                        Text("Android pide desbloquear el dispositivo para guardar o usar el PIN ficticio. Solo se guarda 1234.",
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            if (a.phase == Demo.Phase.AUTH_PENDING) {
                                DemoAction("Simular autenticación correcta", state.busy) { model.message(Demo.Kind.AUTH_OK) }
                                DemoAction("Simular PIN rechazado", state.busy) { model.message(Demo.Kind.AUTH_REJECTED) }
                                DemoAction("Simular sesión vencida", state.busy) { model.message(Demo.Kind.SESSION_EXPIRED) }
                            }
                            if (a.phase in setOf(Demo.Phase.RESULT_PENDING, Demo.Phase.UNCERTAIN)) {
                                DemoAction("Simular confirmación", state.busy) { model.message(Demo.Kind.RECHARGE_OK) }
                                DemoAction("Simular rechazo de recarga", state.busy) { model.message(Demo.Kind.RECHARGE_REJECTED) }
                            }
                            if (a.phase in setOf(Demo.Phase.AUTH_PENDING, Demo.Phase.RESULT_PENDING)) DemoAction("Simular demora", state.busy, model::timeout)
                            if (a.phase == Demo.Phase.SUCCESS) {
                                Text("Mensaje ficticio: ${Demo.SENDER}\nReferencia: ${a.resultReference}")
                                DemoAction("Repetir confirmación (duplicada)", state.busy) { model.message(Demo.Kind.RECHARGE_OK) }
                            }
                            DemoAction(if (a.phase == Demo.Phase.REVIEW) "Volver a los datos" else "Nueva prueba", state.busy) {
                                if (a.phase == Demo.Phase.REVIEW) { bank = a.bank; phone = a.phone; cup = a.cup }
                                model.reset()
                            }
                        }
                        if (state.savedPin && a?.phase != Demo.Phase.REVIEW) DemoAction("Olvidar PIN ficticio", state.busy, model::forgetPin)
                        state.error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                            if (a == null) DemoAction("Descartar prueba guardada", state.busy, model::reset)
                            if (!state.savedPin) DemoAction("Restablecer almacén de PIN ficticio", state.busy, model::forgetPin)
                        }
                        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        HorizontalDivider()
                        bankNotices(a?.subscription ?: state.subscription)
                    }
                }
            }
        }
    }
}

@Composable private fun DemoAction(label: String, busy: Boolean, action: () -> Unit) {
    OutlinedButton(onClick = action, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(label) }
}

@Composable private fun RechargeSummaryRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
