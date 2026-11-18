package com.simple.mobile.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.simple.mobile.domain.transfer.*

@Composable internal fun TransferPanel(state: DashboardState, model: DashboardViewModel,
    pickContact: () -> Unit, launch: (TransferDraft) -> Boolean, dismiss: () -> Unit) {
    var subscription by rememberSaveable { mutableIntStateOf(state.selected) }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var favorite by rememberSaveable { mutableStateOf(false) }
    var manual by rememberSaveable { mutableStateOf(false) }
    var simsOpen by remember { mutableStateOf(false) }
    var recipientsOpen by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Recipient?>(null) }
    val validPhone = TransferPolicy.phone(phone)
    val totals = TransferPolicy.totals(amount)
    val duplicate = state.recipients.firstOrNull { it.phone == validPhone && it.id != editing }
    val line = state.displayLines.firstOrNull { it.subscription == subscription && it.active && it.cubacel }
    val writable = state.transferReady && !state.transferSaving && !state.managingLine
    LaunchedEffect(state.pickedContact) {
        state.pickedContact?.let { (pickedName, pickedPhone) ->
            name = pickedName.take(40); phone = TransferPolicy.phone(pickedPhone) ?: pickedPhone.take(30)
            editing = null; favorite = false; manual = true; model.consumeContact()
        }
    }
    Dialog(onDismissRequest = { if (!state.transferSaving) dismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val focus = LocalFocusManager.current
        val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Transferir saldo", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = dismiss, enabled = !state.transferSaving) { Icon(Icons.Outlined.Close, "Cerrar") }
                    }
                }, bottomBar = {
                    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                            Button(onClick = {
                                focus.clearFocus()
                                if (line != null && validPhone != null && totals != null) {
                                    model.startTransfer(TransferDraft(subscription, line.label, name.trim(), validPhone, totals.received)) { ready ->
                                        val opened = launch(ready)
                                        if (opened) dismiss()
                                        opened
                                    }
                                }
                            }, enabled = writable && !state.busy && line != null && validPhone != null && totals != null,
                                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(min = 52.dp)) {
                                Text(if (state.transferSaving) "Preparando…" else "Continuar en Teléfono")
                            }
                        }
                    }
                }) { padding ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box {
                            TransferSelection("SIM de origen", line?.label ?: "Seleccionar SIM", Icons.Outlined.SimCard, writable) { simsOpen = true }
                            DropdownMenu(expanded = simsOpen, onDismissRequest = { simsOpen = false }) {
                                state.displayLines.filter { it.active && it.cubacel }.forEach { sim ->
                                    DropdownMenuItem(text = { Text(sim.label) }, onClick = { subscription = sim.subscription; simsOpen = false },
                                        leadingIcon = { if (subscription == sim.subscription) Icon(Icons.Outlined.Check, null) })
                                }
                            }
                        }
                        Box {
                            TransferSelection("Destinatario", validPhone?.let { if (name.isBlank()) "+53 $it" else "$name · $it" } ?: "Seleccionar destinatario",
                                Icons.Outlined.PersonOutline, writable) { recipientsOpen = true }
                            DropdownMenu(expanded = recipientsOpen, onDismissRequest = { recipientsOpen = false }) {
                                state.recipients.forEach { recipient ->
                                    DropdownMenuItem(text = { Text("${recipient.name} · ${recipient.phone}") }, onClick = {
                                        editing = recipient.id; name = recipient.name; phone = recipient.phone; favorite = recipient.favorite
                                        manual = false; recipientsOpen = false
                                    })
                                }
                                DropdownMenuItem(text = { Text("Elegir de Contactos") }, onClick = { recipientsOpen = false; pickContact() })
                                DropdownMenuItem(text = { Text("Escribir otro número") }, onClick = {
                                    recipientsOpen = false; manual = true; editing = null; name = ""; phone = ""; favorite = false
                                })
                            }
                        }
                        if (manual) OutlinedTextField(value = phone, onValueChange = { phone = it.take(30) }, label = { Text("Número de destino") },
                            supportingText = { Text("8 dígitos · empieza por 5 o 6") }, isError = phone.isNotBlank() && validPhone == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, enabled = writable, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = amount, onValueChange = { amount = it.take(12) }, label = { Text("El destino recibe") }, suffix = { Text("CUP") },
                            isError = amount.isNotBlank() && totals == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true, enabled = writable, modifier = Modifier.fillMaxWidth())
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraLarge) {
                            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                TransferCostRow("Recibe el destino", "${totals?.received ?: "—"} CUP")
                                TransferCostRow("Comisión fija", "5 CUP")
                                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                                TransferCostRow("Se descuenta de tu saldo", "${totals?.debited ?: "—"} CUP", true)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Key, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("PIN inicial: 1234", modifier = Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(TransferPolicy.DEFAULT_PIN)) }) { Text("Copiar") }
                        }
                        Text("Completa los datos y el PIN en Cubacel. Si cambiaste el PIN, usa el actual.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (validPhone != null) DetailDisclosure("Opciones del destinatario") {
                            OutlinedTextField(value = name, onValueChange = { name = it.filterNot(Char::isISOControl).take(40) }, label = { Text("Nombre") },
                                singleLine = true, enabled = writable, modifier = Modifier.fillMaxWidth())
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(modifier = Modifier.semantics { contentDescription = "Guardar como favorito" }, checked = favorite, onCheckedChange = { favorite = it }, enabled = writable)
                                Text("Favorito", modifier = Modifier.weight(1f))
                                TextButton(onClick = { model.saveRecipient(editing, name, phone, favorite) },
                                    enabled = writable && TransferPolicy.name(name) != null && duplicate == null) { Text("Guardar") }
                                editing?.let { id -> state.recipients.firstOrNull { it.id == id }?.let { recipient ->
                                    IconButton(onClick = { deleting = recipient }, enabled = writable) { Icon(Icons.Outlined.DeleteOutline, "Eliminar destinatario") }
                                } }
                            }
                            TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(validPhone)) }) { Text("Copiar número") }
                            if (duplicate != null) Text("Ya guardado como ${duplicate.name}.", style = MaterialTheme.typography.bodySmall)
                        }
                        if (line == null) Text("Selecciona una SIM Cubacel activa.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        if (!state.transferReady) Text("Reintenta el almacenamiento en Ajustes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        if (state.busy) Text("Espera a que termine la consulta.", style = MaterialTheme.typography.bodySmall)
                        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                        if (state.transferSaving) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
    deleting?.let { recipient ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("¿Eliminar a ${recipient.name}?") },
            confirmButton = { TextButton(onClick = {
                model.deleteRecipient(recipient.id)
                if (editing == recipient.id) { editing = null; name = ""; phone = ""; favorite = false }
                deleting = null
            }, enabled = writable) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } })
    }
}

@Composable private fun TransferSelection(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleSmall)
            }
            Icon(Icons.Outlined.ExpandMore, null)
        }
    }
}

@Composable private fun TransferCostRow(label: String, value: String, total: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(value, style = if (total) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
            fontWeight = if (total) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable internal fun TransferHistory(records: List<TransferRecord>, saving: Boolean, result: (String, Boolean) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var marking by remember { mutableStateOf<Pair<TransferRecord, Boolean>?>(null) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Transferencias de esta línea", style = MaterialTheme.typography.titleLarge)
            Text("Registro local · no es un comprobante del operador", style = MaterialTheme.typography.bodySmall)
            if (records.isEmpty()) {
                Icon(Icons.Outlined.SwapHoriz, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Text("Todavía no hay transferencias", style = MaterialTheme.typography.titleMedium)
                Text("Las que prepares desde Inicio aparecerán aquí.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            (if (expanded) records else records.take(3)).forEach { record ->
                HorizontalDivider()
                Text("${record.amount} CUP", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Para ${record.name.ifBlank { "+53 ${record.phone}" }}", style = MaterialTheme.typography.bodyMedium)
                Text(java.text.SimpleDateFormat("d MMM yyyy · HH:mm", java.util.Locale.forLanguageTag("es")).format(java.util.Date(record.at)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(when (record.status) {
                    "PREPARED" -> "Preparada · apertura no verificada"
                    "OPENED" -> "Menú abierto · resultado sin confirmar"
                    "USER_CONFIRMED" -> "Realizada según tu confirmación"
                    "NOT_COMPLETED" -> "No realizada según tu confirmación"
                    else -> "No se pudo abrir el menú"
                }, color = MaterialTheme.colorScheme.primary)
                key(record.id) {
                    DetailDisclosure("Detalles y confirmación") {
                        Text("Número: +53 ${record.phone}")
                        if (record.status in setOf("OPENED", "USER_CONFIRMED", "NOT_COMPLETED")) {
                            Row {
                                TextButton(onClick = { marking = record to true }, enabled = !saving) { Text("La realicé") }
                                TextButton(onClick = { marking = record to false }, enabled = !saving) { Text("No realizada") }
                            }
                        }
                    }
                }
            }
            if (records.size > 3) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Ver menos" else "Ver todas (${records.size})") }
        }
    }
    marking?.let { (record, confirmed) ->
        AlertDialog(onDismissRequest = { marking = null }, title = { Text(if (confirmed) "¿Confirmas que la realizaste?" else "¿Marcar como no realizada?") },
            text = { Text("${record.amount} CUP para +53 ${record.phone}. Esta es tu anotación personal; SIMple no verifica el envío con el operador.") },
            confirmButton = { TextButton(onClick = { result(record.id, confirmed); marking = null }, enabled = !saving) { Text("Guardar anotación") } },
            dismissButton = { TextButton(onClick = { marking = null }) { Text("Cancelar") } })
    }
}
