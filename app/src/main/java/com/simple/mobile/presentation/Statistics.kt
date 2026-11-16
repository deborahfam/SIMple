package com.simple.mobile.presentation

import android.app.DatePickerDialog
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simple.mobile.domain.carrier.CubacelQuery
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Composable internal fun Statistics(state: DashboardState, model: DashboardViewModel, now: Long,
    onReminders: (Boolean) -> Unit, onNotificationSettings: () -> Unit, onRefresh: () -> Unit) {
    val context = LocalContext.current
    var days by rememberSaveable { mutableIntStateOf(7) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val line = state.displayLines.firstOrNull { it.subscription == state.selected }
    val personal = state.rechargeDates[state.selected]
    val target = personal ?: StatisticsPolicy.recharge(state.entries, state.selected, now)
    val budget = StatisticsPolicy.budget(state.entries, state.selected, target, now)
    val usage = state.mobileUsage
    LaunchedEffect(Unit) { model.loadMobileUsage(days) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner, days) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) model.loadMobileUsage(days)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StatsCard {
            Text("Hasta tu próxima recarga", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(line?.label ?: "Selecciona una línea")
            Text(target?.toString() ?: "Fecha pendiente", style = MaterialTheme.typography.headlineSmall)
            Text(if (personal != null) "Fecha elegida por ti · hora de Cuba" else "Fecha informada por Cubacel, si está disponible. No confirma el cupo de recarga.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = {
                val initial = target?.takeIf { it >= StatisticsPolicy.date(now) } ?: StatisticsPolicy.date(now).plusDays(1)
                DatePickerDialog(context, { _, y, m, d -> model.setRechargeDate(state.selected, LocalDate.of(y, m + 1, d)) }, initial.year, initial.monthValue - 1, initial.dayOfMonth).apply {
                    datePicker.minDate = System.currentTimeMillis()
                }.show()
            }, enabled = line != null && state.storageReady && !state.managingLine) { Text("Elegir fecha de esta SIM") }
            if (personal != null) TextButton(onClick = { model.setRechargeDate(state.selected, null) }, enabled = !state.managingLine) { Text("Usar fecha de Cubacel") }
            HorizontalDivider()
            Text(budget.dailyBytes?.let { "${StatisticsPolicy.bytes(it)} / día" } ?: "Presupuesto pendiente", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(budget.message)
            budget.remaining?.let { Text("${StatisticsPolicy.bytes(it)} de Internet general en la última consulta. No incluye LTE ni bolsas restringidas.", style = MaterialTheme.typography.bodySmall) }
            Text("Es un máximo orientativo basado en la última lectura; no un objetivo de gasto ni una medición en tiempo real.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onRefresh, enabled = line?.active == true && line.cubacel && state.storageReady && !state.busy && !state.managingLine) { Text("Actualizar recursos") }
        }
        StatsCard {
            Text("Cambios en tus recursos", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 30).forEach { n -> FilterChip(selected = days == n, onClick = { days = n; showAll = false; model.loadMobileUsage(n) }, enabled = !usage.loading, label = { Text("$n días") }) }
            }
            Text("Reducciones observadas de ${line?.label ?: "la línea seleccionada"}. No equivalen a un desglose de gastos: pueden incluir transferencias y otros cargos.", style = MaterialTheme.typography.bodySmall)
            val reductions = remember(state.entries, state.selected, days, now) { StatisticsPolicy.reductions(state.entries, state.selected, days, now) }
            reductions.forEach { r ->
                val title = when (r.query) { CubacelQuery.BALANCE -> "Saldo"; CubacelQuery.DATA -> "Internet general"; CubacelQuery.VOICE -> "Llamadas"; else -> "SMS" }
                val value = r.amount?.let { amount -> when (r.query) {
                    CubacelQuery.BALANCE -> "${amount.setScale(2, RoundingMode.DOWN)} CUP"
                    CubacelQuery.DATA -> StatisticsPolicy.bytes(amount.toLong())
                    CubacelQuery.VOICE -> "${amount.divide(BigDecimal(60), 1, RoundingMode.DOWN)} min"
                    else -> "$amount SMS"
                } } ?: "Sin comparación"
                HorizontalDivider()
                Text("$title · $value", fontWeight = FontWeight.SemiBold)
                if (r.intervals > 0) Text("${r.intervals} intervalos comparables · ${StatisticsPolicy.date(r.from!!)} a ${StatisticsPolicy.date(r.to!!)}", style = MaterialTheme.typography.bodySmall)
                else Text("Necesitamos dos lecturas comparables dentro del período.", style = MaterialTheme.typography.bodySmall)
                if (r.omitted > 0) Text("${r.omitted} intervalos excluidos por cambios de cuota, vigencia o datos incompletos.", style = MaterialTheme.typography.bodySmall)
            }
            Text("Resumen parcial del historial conservado (hasta 300 lecturas). Compras y recargas detectadas interrumpen la comparación; las no detectadas pueden alterar el resultado.", style = MaterialTheme.typography.bodySmall)
        }
        StatsCard {
            Text("Datos móviles por aplicación", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("Todo el teléfono · todas las SIM · últimos ${usage.days} días", color = MaterialTheme.colorScheme.primary)
            Text("Android suma descargas y subidas. No incluye Wi-Fi, no separa tus tarjetas y puede diferir del consumo cobrado por Cubacel. Los datos se consultan solo en este dispositivo.", style = MaterialTheme.typography.bodySmall)
            if (usage.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!usage.allowed && !usage.loading) Text("Autoriza el acceso de uso en Android para ver las aplicaciones. El resto de estadísticas funciona sin este permiso.")
            OutlinedButton(onClick = {
                try { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                catch (_: Exception) { model.notify("Abre Ajustes de Android → Acceso especial → Acceso de uso → SIMple.") }
            }) { Text(if (usage.allowed) "Gestionar acceso de uso" else "Permitir acceso de uso") }
            usage.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (usage.allowed && !usage.loading && usage.error == null) {
                if (usage.apps.isEmpty()) Text("Android aún no tiene tráfico móvil registrado para este período.")
                else {
                    Text("Registrado: ${StatisticsPolicy.bytes(usage.apps.sumOf { it.bytes })}", fontWeight = FontWeight.SemiBold)
                    val max = usage.apps.first().bytes.toFloat()
                    usage.apps.take(if (showAll) usage.apps.size else 8).forEach { app ->
                        Text(app.name, fontWeight = FontWeight.SemiBold)
                        Text(StatisticsPolicy.bytes(app.bytes))
                        LinearProgressIndicator(progress = { (app.bytes / max).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    }
                    if (usage.apps.size > 8) TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Mostrar menos" else "Ver todas (${usage.apps.size})") }
                }
            }
            TextButton(onClick = { model.loadMobileUsage(days) }, enabled = !usage.loading) { Text("Actualizar estadísticas de Android") }
        }
        StatsCard {
            Text("Avisos de recarga", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("Cada tarjeta usa su propia fecha. Las fechas elegidas por ti avisan a partir de las 9:00, hora de Cuba. Android puede retrasar la notificación.")
            Row {
                Text("Activar recordatorios", modifier = Modifier.weight(1f))
                Switch(checked = state.remindersEnabled, onCheckedChange = onReminders)
            }
            if (state.remindersEnabled && !state.notificationsAllowed) Text("Android tiene bloqueadas las notificaciones.", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onNotificationSettings) { Text("Ajustes de notificaciones") }
        }
    }
}

@Composable private fun StatsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
