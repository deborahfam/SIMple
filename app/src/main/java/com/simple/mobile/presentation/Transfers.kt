package com.simple.mobile.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable internal fun TransferPanel(state: DashboardState, model: DashboardViewModel,
    pickContact: () -> Unit, launch: (TransferDraft) -> Boolean, dismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var favorite by rememberSaveable { mutableStateOf(false) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var review by remember { mutableStateOf<TransferDraft?>(null) }
    var deleting by remember { mutableStateOf<Recipient?>(null) }
    val validPhone = TransferPolicy.phone(phone)
    val duplicate = state.recipients.firstOrNull { it.phone == validPhone && it.id != editing }
    val validAmount = TransferPolicy.amount(amount)
    val line = state.lines.firstOrNull { it.subscription == state.selected && it.cubacel }
    val writable = state.transferReady && !state.transferSaving && !state.managingLine
    LaunchedEffect(state.pickedContact) {
        state.pickedContact?.let { (pickedName, pickedPhone) ->
            name = pickedName.take(40); phone = TransferPolicy.phone(pickedPhone) ?: pickedPhone.take(30)
            editing = null; favorite = false; model.consumeContact()
        }
    }
    Dialog(onDismissRequest = { if (!state.transferSaving) dismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Transferir saldo", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = dismiss, enabled = !state.transferSaving) { Icon(Icons.Outlined.Close, "Cerrar") }
                    }
                }, bottomBar = {
                    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Revisa los datos antes de continuar en Teléfono", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth())
                            Button(onClick = { review = TransferDraft(line!!.subscription, line.label, name.trim(), validPhone!!, validAmount!!) },
                                enabled = writable && !state.busy && line != null && validPhone != null && validAmount != null,
                                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(min = 52.dp)) { Text("Revisar transferencia") }
                        }
                    }
                }) { padding ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        FlowProgress(0, listOf("Prepara tu transferencia", "Revisa los datos"))
                        Text("Elige a quién enviar. Revisarás los datos antes de abrir el menú de Cubacel.", style = MaterialTheme.typography.bodyMedium)
                        Text("Desde tu SIM", fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.lines.filter { it.cubacel }.forEach { sim ->
                                FilterChip(selected = sim.subscription == state.selected, onClick = { model.select(sim.subscription) },
                                    enabled = !state.transferSaving, label = { Text(sim.label) })
                            }
                        }
                        if (line == null) Text("Conecta una SIM Cubacel para transferir. Puedes guardar destinatarios mientras tanto.", color = MaterialTheme.colorScheme.error)
                        if (!state.transferReady) Text("Los destinatarios no están disponibles. Reintenta el almacenamiento desde Ajustes.", color = MaterialTheme.colorScheme.error)
                        if (state.recipients.isNotEmpty()) {
                            Text("Destinatarios guardados", fontWeight = FontWeight.Bold)
                            (if (showAll) state.recipients else state.recipients.take(5)).forEach { recipient ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = {
                                        editing = recipient.id; name = recipient.name; phone = recipient.phone; favorite = recipient.favorite
                                    }, enabled = writable, modifier = Modifier.weight(1f)) {
                                        Column(Modifier.fillMaxWidth()) {
                                            Text((if (recipient.favorite) "★ " else "") + recipient.name, fontWeight = FontWeight.Bold)
                                            Text("+53 ${recipient.phone}")
                                        }
                                    }
                                    IconButton(onClick = { deleting = recipient }, enabled = writable) { Icon(Icons.Outlined.DeleteOutline, "Eliminar ${recipient.name}") }
                                }
                            }
                            if (state.recipients.size > 5) TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Ver menos" else "Ver todos (${state.recipients.size})") }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = pickContact, enabled = writable) { Text("Elegir de Contactos") }
                            TextButton(onClick = { editing = null; name = ""; phone = ""; favorite = false }, enabled = writable) { Text("Nuevo destinatario") }
                        }
                        OutlinedTextField(value = name, onValueChange = { name = it.filterNot(Char::isISOControl).take(40) },
                            label = { Text("Nombre o apodo") }, singleLine = true, enabled = writable, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = phone, onValueChange = { phone = it.take(30) }, label = { Text("Número Cubacel") },
                            supportingText = { Text("8 dígitos, empieza por 5 o 6. También puedes usar +53.") },
                            isError = phone.isNotBlank() && validPhone == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true, enabled = writable, modifier = Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(modifier = Modifier.semantics { contentDescription = "Guardar como favorito" }, checked = favorite, onCheckedChange = { favorite = it }, enabled = writable)
                            Text("Favorito", modifier = Modifier.weight(1f))
                            TextButton(onClick = { model.saveRecipient(editing, name, phone, favorite) },
                                enabled = writable && TransferPolicy.name(name) != null && validPhone != null && duplicate == null) { Text(if (editing == null) "Guardar" else "Guardar cambios") }
                        }
                        if (duplicate != null) Text("Este número ya está guardado como ${duplicate.name}. Selecciónalo arriba para editarlo.", style = MaterialTheme.typography.bodySmall)
                        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
                        Text("Los destinatarios se guardan cifrados en este teléfono. Solo se lee el contacto que elijas.", style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider()
                        OutlinedTextField(value = amount, onValueChange = { amount = it.take(12) }, label = { Text("Importe a transferir · CUP") },
                            supportingText = { Text("Importe positivo, hasta dos decimales.") }, isError = amount.isNotBlank() && validAmount == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = writable,
                            modifier = Modifier.fillMaxWidth())
                        Text("Cargo publicado: 5 CUP por transferencia. Confirma las condiciones en Cubacel antes de aceptar.", style = MaterialTheme.typography.bodySmall)
                        if (state.transferSaving) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
    review?.let { draft ->
        AlertDialog(onDismissRequest = { if (!state.transferSaving) review = null }, title = { Text("Revisa antes de continuar") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FlowProgress(1, listOf("Prepara tu transferencia", "Revisa los datos"))
                    Text("Desde: ${draft.line}")
                    Text("Para: ${draft.name.ifBlank { "Destinatario" }}\n+53 ${draft.phone}", fontWeight = FontWeight.Bold)
                    Text("Importe: ${draft.amount} CUP", style = MaterialTheme.typography.headlineSmall)
                    Text("Cargo publicado: 5 CUP\nTotal de referencia: ${draft.amount.toBigDecimal().add(TransferPolicy.publishedFee).stripTrailingZeros().toPlainString()} CUP")
                    Text("Abriremos *234#. Tendrás que introducir el número, importe y PIN en ese menú y elegir la SIM indicada. SIMple no envía estos datos ni guarda tu PIN.")
                    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                    TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(draft.phone)) }) { Text("Copiar número") }
                    Text("Hasta 3 transferencias diarias según ETECSA. Abrir el menú no confirma que se haya enviado saldo.", style = MaterialTheme.typography.bodySmall)
                }
            }, confirmButton = {
                TextButton(onClick = {
                    model.startTransfer(draft) { ready ->
                        val opened = launch(ready)
                        if (opened) { review = null; dismiss() }
                        opened
                    }
                }, enabled = writable && !state.busy && state.lines.any { it.subscription == draft.subscription && it.cubacel }) { Text("Continuar en Teléfono") }
            }, dismissButton = { TextButton(onClick = { review = null }, enabled = !state.transferSaving) { Text("Volver") } })
    }
    deleting?.let { recipient ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("¿Eliminar a ${recipient.name}?") },
            text = { Text("Se eliminará de tus destinatarios guardados. Sus registros de transferencia permanecerán en el historial.") },
            confirmButton = { TextButton(onClick = {
                model.deleteRecipient(recipient.id)
                if (editing == recipient.id) { editing = null; name = ""; phone = ""; favorite = false }
                deleting = null
            }, enabled = writable) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } })
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
