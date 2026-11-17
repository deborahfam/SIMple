package com.simple.mobile.presentation.recharge

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simple.mobile.SimpleApplication
import com.simple.mobile.domain.recharge.*
import com.simple.mobile.presentation.DashboardState
import com.simple.mobile.presentation.FlowProgress

@Composable fun RechargeScreen(dashboard: DashboardState, onClose: () -> Unit, onPermissions: () -> Unit,
    bankNotices: @Composable (Int) -> Unit) {
    val context = LocalContext.current
    val controller = (context.applicationContext as SimpleApplication).bankRecharges
    val state by controller.state.collectAsStateWithLifecycle()
    val attempt = state.attempt
    val initialSub by rememberSaveable { mutableIntStateOf(dashboard.selected) }
    val subscription = attempt?.subscription ?: initialSub
    val line = attempt?.line ?: dashboard.displayLines.firstOrNull { it.subscription == subscription }?.label.orEmpty()
    var formStep by rememberSaveable { mutableIntStateOf(0) }
    var destination by rememberSaveable { mutableIntStateOf(dashboard.selected) }
    val phone = dashboard.linePhones[destination].orEmpty()
    var cup by rememberSaveable { mutableStateOf("360") }
    var pin by remember(attempt?.id) { mutableStateOf("") }
    var authConfirmed by remember(attempt?.id) { mutableStateOf(false) }
    var outcomeReviewed by remember(attempt?.id) { mutableStateOf(false) }
    val eligible = dashboard.lines.any { it.subscription == subscription && it.active && it.cubacel }
    val permitted = dashboard.permission && context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
    val available = eligible && permitted && dashboard.storageReady && !dashboard.busy && !dashboard.managingLine && !dashboard.transferSaving
    val debitPhase = attempt?.phase in setOf(BankPhase.SENDING, BankPhase.RESPONSE, BankPhase.UNKNOWN)
    val step = if (debitPhase) 3 else if (attempt != null) 2 else formStep
    val scroll = rememberScrollState()
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false, securePolicy = SecureFlagPolicy.SecureOn)) {
        val focus = LocalFocusManager.current
        LaunchedEffect(step, attempt?.phase) { scroll.scrollTo(0); focus.clearFocus() }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (attempt == null && formStep > 0) IconButton(onClick = { pin = ""; formStep-- }, enabled = !state.busy) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver al paso anterior")
                        }
                        Text("Recargar", modifier = Modifier.weight(1f).padding(start = 8.dp).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = { pin = ""; onClose() }) { Icon(Icons.Outlined.Close, "Cerrar recarga") }
                    }
                }, bottomBar = {
                    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                                Text("Metropolitano · cuenta CUP asociada", style = MaterialTheme.typography.titleSmall)
                                Text(attempt?.let { "${it.cup} CUP para +53 ${it.phone}" } ?: "${MetropolitanoProtocol.amount(cup)?.let { "$it CUP" } ?: "Prepara tu recarga"}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            val button = Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(min = 52.dp)
                            when {
                                state.busy -> Button(onClick = {}, enabled = false, modifier = button) { Text("Esperando respuesta…") }
                                attempt == null && formStep < 2 -> Button(onClick = { focus.clearFocus(); formStep++ }, modifier = button,
                                    enabled = state.ready && (formStep == 0 || (MetropolitanoProtocol.phone(phone) != null && MetropolitanoProtocol.amount(cup) != null))) { Text(if (formStep == 0) "Elegir otro importe" else "Continuar") }
                                !permitted && (attempt == null || attempt.phase == BankPhase.AUTH_RESPONSE) -> Button(onClick = onPermissions, modifier = button) { Text("Permitir acceso al teléfono") }
                                attempt == null -> Button(onClick = {
                                    val value = pin.toCharArray(); pin = ""; focus.clearFocus()
                                    controller.authenticate(subscription, line, phone.trim(), MetropolitanoProtocol.amount(cup)!!, value)
                                }, modifier = button, enabled = available && state.ready && MetropolitanoProtocol.phone(phone) != null && MetropolitanoProtocol.amount(cup) != null && MetropolitanoProtocol.validPin(pin.toCharArray())) { Text("Autenticar en Metropolitano") }
                                attempt.phase == BankPhase.AUTH_RESPONSE -> Button(onClick = { controller.confirm(attempt.id, authConfirmed) },
                                    modifier = button, enabled = available && authConfirmed && state.ready) { Text("Confirmar recarga · ${attempt.cup} CUP") }
                                else -> OutlinedButton(onClick = onClose, modifier = button) { Text("Volver a mi línea") }
                            }
                        }
                    }
                }) { padding ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(scroll).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        FlowProgress(step, listOf("Tu recarga", "Línea e importe", "Revisa y confirma", "Tu solicitud"))
                        if (!state.ready && state.error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                        if (!eligible) Text("Selecciona en Inicio la SIM Cubacel registrada en Metropolitano.", color = MaterialTheme.colorScheme.error)
                        if (!dashboard.storageReady) Text("Reintenta el almacenamiento desde Ajustes antes de recargar.", color = MaterialTheme.colorScheme.error)
                        if (dashboard.busy) Text("Espera a que termine la consulta de tu línea.")
                        if (attempt == null && formStep == 0) {
                            Text("Recarga desde tu banco", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large,
                                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)) {
                                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Icon(Icons.Outlined.AccountBalance, null, tint = MaterialTheme.colorScheme.primary)
                                    Column(Modifier.weight(1f)) {
                                        Text("Metropolitano", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        Text("Cuenta CUP asociada a tu línea", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Text("Necesitas tener el servicio bancario registrado en esta SIM. SIMple enviará las solicitudes por USSD desde tu teléfono.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Línea: ${line.ifBlank { "Sin SIM seleccionada" }}", style = MaterialTheme.typography.titleSmall)
                            RechargeLineSelector(dashboard, destination) { destination = it }
                            Button(onClick = { cup = "360"; formStep = 2 }, enabled = MetropolitanoProtocol.phone(phone) != null && state.ready,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                                Text("Recargar 360 CUP")
                            }
                            Text("Revisa y autentícate para confirmar el cargo.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if (attempt == null && formStep == 1) {
                            Text("Línea e importe", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            RechargeLineSelector(dashboard, destination) { destination = it }
                            OutlinedTextField(cup, { cup = it.take(8) }, modifier = Modifier.fillMaxWidth(), label = { Text("Importe") }, suffix = { Text("CUP") },
                                supportingText = { Text("De 1 a 360 CUP, sin decimales") }, isError = cup.isNotEmpty() && MetropolitanoProtocol.amount(cup) == null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                            Text("El operador comprobará el límite de recarga disponible del número de destino.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraLarge) {
                                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("TU RECARGA", style = MaterialTheme.typography.labelLarge)
                                    Text("${attempt?.cup ?: MetropolitanoProtocol.amount(cup)} CUP", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                                    Text("Para +53 ${attempt?.phone ?: phone}", style = MaterialTheme.typography.titleMedium)
                                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                                    Text("Metropolitano · cuenta CUP asociada")
                                    Text("Desde $line", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (attempt == null) {
                                Text("Primero, autentícate", style = MaterialTheme.typography.titleLarge)
                                Text("La autenticación no envía la recarga. Después revisarás la respuesta y confirmarás el cargo.")
                                OutlinedTextField(pin, { pin = it.filter { c -> c in '0'..'9' }.take(4) }, label = { Text("PIN bancario · 4 dígitos") },
                                    modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true)
                                SavedBankPin(subscription, pin) { pin = it }
                                Text("El PIN sólo se guarda si lo eliges. No cambies de banco ni de SIM entre la autenticación y la recarga.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text(when(attempt.phase) {
                                    BankPhase.AUTH_SENDING -> "Autenticando en Metropolitano"
                                    BankPhase.AUTH_RESPONSE -> "Comprueba la autenticación"
                                    BankPhase.AUTH_UNKNOWN -> "Autenticación sin confirmar"
                                    BankPhase.AUTH_REQUIRED -> "Vuelve a autenticarte"
                                    BankPhase.SENDING -> "Enviando tu solicitud"
                                    BankPhase.RESPONSE -> "Respuesta a la solicitud"
                                    BankPhase.UNKNOWN -> "Resultado sin confirmar"
                                    BankPhase.NOT_SENT -> "La solicitud no se envió"
                                }, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                                state.response?.let { reply ->
                                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
                                        Text(reply, Modifier.fillMaxWidth().padding(16.dp))
                                    }
                                }
                                if (attempt.phase == BankPhase.AUTH_RESPONSE) {
                                    Text("Revisa el SMS de PAGOxMOVIL: debe confirmar la autenticación en Metropolitano de esta línea. Una respuesta USSD por sí sola no la confirma.")
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(authConfirmed, { authConfirmed = it })
                                        Text("He recibido la confirmación de autenticación de Metropolitano.", style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Text("Al confirmar se enviará una única solicitud de ${attempt.cup} CUP. La revisión vence a los 5 minutos.", style = MaterialTheme.typography.bodySmall)
                                } else if (debitPhase) {
                                    Text("Comprueba el SMS y los recursos del móvil de destino. Recibir una respuesta no garantiza que la recarga se completó. No repitas el pago si el resultado es incierto.")
                                } else if (!state.busy) {
                                    Text("No se ha enviado ninguna recarga. Revisa los permisos, la SIM y el SMS del banco antes de iniciar otra autenticación.")
                                }
                                if (!state.busy) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(outcomeReviewed, { outcomeReviewed = it })
                                        Text(if (debitPhase) "He revisado el resultado antes de iniciar otra recarga." else "He revisado la respuesta y quiero cerrar esta solicitud.", style = MaterialTheme.typography.bodySmall)
                                    }
                                    TextButton(onClick = { controller.acknowledge(); formStep = 0; pin = ""; authConfirmed = false }, enabled = outcomeReviewed) { Text("Nueva recarga") }
                                }
                            }
                        }
                        bankNotices(subscription)
                    }
                }
            }
        }
    }
}

@Composable private fun RechargeLineSelector(dashboard: DashboardState, selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val destination = dashboard.displayLines.firstOrNull { it.subscription == selected }
    val phone = dashboard.linePhones[selected].orEmpty()
    Text("Línea que recibe la recarga", style = MaterialTheme.typography.titleSmall)
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            shape = MaterialTheme.shapes.large) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(destination?.label ?: "Seleccionar línea")
                Text(if (phone.isBlank()) "Número sin configurar" else "+53 $phone", style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Outlined.ExpandMore, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            dashboard.displayLines.forEach { line ->
                DropdownMenuItem(text = {
                    Column {
                        Text(line.label)
                        Text(dashboard.linePhones[line.subscription]?.let { "+53 $it" } ?: "Número sin configurar", style = MaterialTheme.typography.bodySmall)
                    }
                }, onClick = { onSelect(line.subscription); expanded = false },
                    leadingIcon = { if (line.subscription == selected) Icon(Icons.Outlined.Check, null) })
            }
        }
    }
    if (phone.isBlank()) Text("Guarda el número una sola vez en Ajustes → Editar línea para poder recargarla.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
