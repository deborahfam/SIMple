package com.simple.mobile.presentation.recharge

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simple.mobile.presentation.DashboardState
import com.simple.mobile.presentation.DetailDisclosure
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun BankRechargeNotices(state: DashboardState, onEnabled: (Boolean) -> Unit, onSync: () -> Unit) {
    val notices = state.bankNotices.filter { it.receivingSubscription == state.selected }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(if (state.smsStatus.bankEnabled) "Lectura de avisos bancarios activada" else "Lectura de avisos bancarios desactivada",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        notices.firstOrNull()?.let {
            Text("Último aviso: rechazo por límite de recarga · ${it.destination}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (state.bankNoticeError) Text("No se pudieron abrir los avisos guardados. Reabre la app para reintentarlo.", color = MaterialTheme.colorScheme.error)
        key(state.selected) {
            DetailDisclosure("Avisos bancarios${if (notices.isNotEmpty()) " (${notices.size})" else ""}") {
                BankRechargeNoticeDetails(state, onEnabled, onSync)
            }
        }
    }
}

@Composable private fun BankRechargeNoticeDetails(state: DashboardState, onEnabled: (Boolean) -> Unit, onSync: () -> Unit) {
    var showOlder by remember(state.selected) { mutableStateOf(false) }
    val status = state.smsStatus
    val notices = state.bankNotices.filter { it.receivingSubscription == state.selected }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Respuestas de recarga", style = MaterialTheme.typography.titleMedium)
        Row {
            Text("Leer avisos de PAGOxMOVIL", modifier = Modifier.weight(1f))
            Switch(modifier = Modifier.semantics { contentDescription = "Activar lectura de PAGOxMOVIL" }, checked = status.bankEnabled, onCheckedChange = onEnabled, enabled = !state.managingLine && !status.syncing)
        }
        Text("Reconoce rechazos por límite de recarga de 30 días. No envía pagos ni cambia tu saldo.", style = MaterialTheme.typography.bodySmall)
        if (status.bankEnabled) {
            val connected = status.enabled && state.smsReadAllowed && state.smsReceiveAllowed && state.permission
            if (!connected) {
                Text("Falta activar la conexión SMS o conceder sus permisos.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { onEnabled(true) }, enabled = !status.syncing) { Text("Conectar recepción SMS") }
            } else {
                OutlinedButton(onClick = onSync, enabled = !status.syncing) { Text(if (status.syncing) "Revisando…" else "Buscar respuestas recibidas") }
            }
            status.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
        if (notices.isEmpty()) Text("Sin rechazos reconocidos para los SMS recibidos en esta SIM. La ausencia de un SMS no confirma éxito ni fallo.", style = MaterialTheme.typography.bodySmall)
        val date = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        val timestamp = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm").withZone(ZoneId.systemDefault())
        notices.take(if (showOlder) 3 else 1).forEach { notice ->
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Rechazo por límite de recarga", style = MaterialTheme.typography.titleSmall)
                    Text("Móvil de destino: ${notice.destination}")
                    Text("Límite informado: ${notice.limitCup} CUP en 30 días")
                    Text("El SMS indica: recargar después del ${date.format(notice.afterDate)}")
                    Text("PAGOxMOVIL · ${timestamp.format(Instant.ofEpochMilli(notice.receivedAt))}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (notices.isNotEmpty()) Text("SMS recibido en la SIM seleccionada; el destino puede ser otro móvil. El límite informado no es un cargo ni una consulta de saldo.", style = MaterialTheme.typography.bodySmall)
        if (notices.size > 1) TextButton(onClick = { showOlder = !showOlder }) {
            Text(if (showOlder) "Mostrar solo el más reciente" else "Ver avisos anteriores (hasta 3)")
        }
    }
}
