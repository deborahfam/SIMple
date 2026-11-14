package com.simple.mobile.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simple.mobile.domain.carrier.CubacelQuery
import com.simple.mobile.domain.model.CarrierReading
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink = Color(0xFF29223D)
private val Muted = Color(0xFF80788F)
private val Paper = Color(0xFFF7F5FC)
private val Mint = Color(0xFFE0D4FF)
private val Teal = Color(0xFF7353B6)
private val Navy = Color(0xFF35234F)

@Composable fun Dashboard(model: DashboardViewModel, onConnect: () -> Unit,
    onQuery: (CubacelQuery) -> Unit, onRefreshAll: () -> Unit, onReminders: (Boolean) -> Unit, onNotificationSettings: () -> Unit, onSyncSms: () -> Unit, onSettings: () -> Unit, onAppSettings: () -> Unit,
    onDial: (String) -> Unit, onRechargeSite: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); kotlinx.coroutines.delay(30_000) }
    }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var recharge by remember { mutableStateOf(false) }
    var confirmDial by remember { mutableStateOf<String?>(null) }
    var delete by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); model.dismissMessage() } }
    MaterialTheme(colorScheme = lightColorScheme(primary = Teal, secondary = Navy, background = Paper, surface = Color.White,
        onSurface = Ink, onBackground = Ink)) {
        Scaffold(containerColor = Paper, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                listOf("Inicio" to Icons.Outlined.SpaceDashboard, "Historial" to Icons.Outlined.History,
                    "Ajustes" to Icons.Outlined.Tune).forEachIndexed { index, item ->
                    NavigationBarItem(selected = page == index, onClick = { page = index }, icon = { Icon(item.second, null) },
                        label = { Text(item.first) }, colors = NavigationBarItemDefaults.colors(indicatorColor = Mint))
                }
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                LazyColumn(Modifier.widthIn(max = 640.dp).fillMaxWidth(), contentPadding = PaddingValues(22.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Navy), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.SimCard, null, tint = Mint, modifier = Modifier.size(23.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text("SIMple", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Spacer(Modifier.weight(1f))
                            Surface(color = Color(0xFFEEE8F7), shape = CircleShape) {
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(5.dp).background(Teal, CircleShape)); Spacer(Modifier.width(5.dp))
                                    Text("SOLO EN TU MÓVIL", color = Teal, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    item {
                        Column {
                            Text(listOf("Tu línea, en un vistazo.", "Cada consulta cuenta.", "A tu manera.")[page], fontSize = 28.sp,
                                lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Text(listOf("Menos dudas. Más conexión.", "Lecturas guardadas automáticamente en este móvil.",
                                "Controla tus líneas y la privacidad de tus datos.")[page], fontSize = 13.sp, color = Muted,
                                modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                    item {
                        if (!state.permission) {
                            WhiteCard {
                                Icon(Icons.Outlined.SimCard, null, tint = Teal)
                                Text("Conecta tu SIM", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("Permite acceso al teléfono para detectar tus líneas y consultar Cubacel.", color = Muted, fontSize = 13.sp)
                                Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) { Text("Conectar mi línea") }
                            }
                        } else if (state.lines.isEmpty()) {
                            WhiteCard {
                                Text("No hay SIM activas", fontWeight = FontWeight.Bold)
                                Text("Activa o inserta una tarjeta para consultar. Tu historial sigue disponible.", color = Muted, fontSize = 13.sp)
                                OutlinedButton(onClick = onSettings) { Text("Gestionar tarjetas SIM") }
                            }
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(state.lines, key = { it.subscription }) { line ->
                                    val selected = state.selected == line.subscription
                                    Surface(onClick = { model.select(line.subscription) }, shape = RoundedCornerShape(18.dp),
                                        color = if (selected) Navy else Color.White) {
                                        Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Outlined.SimCard, null, tint = if (selected) Mint else Muted, modifier = Modifier.size(21.dp))
                                            Spacer(Modifier.width(10.dp))
                                            Column {
                                                Text("SIM ${line.slot + 1} · ${line.carrier.ifBlank { "Operador" }}", color = if (selected) Color.White else Ink,
                                                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                Text(if (line.cubacel) "Cubacel · consultas disponibles" else "Operador sin integración", color = if (selected) Color(0xFFD6C8E8) else Muted, fontSize = 10.sp)
                                            }
                                            if (selected) { Spacer(Modifier.width(12.dp)); Icon(Icons.Outlined.CheckCircle, null, tint = Mint, modifier = Modifier.size(17.dp)) }
                                        }
                                    }
                                }
                                item { OutlinedIconButton(onClick = onSettings, modifier = Modifier.padding(top = 6.dp)) { Icon(Icons.Outlined.Add, "Añadir o activar SIM") } }
                            }
                        }
                    }
                    val entries = state.entries.filter { it.subscription == state.selected }
                    if (page == 0) {
                        val balance = entries.firstOrNull { it.reading.query() == CubacelQuery.BALANCE && it.reading.balanceCup() != null }?.reading
                        val data = entries.firstOrNull { it.reading.query() == CubacelQuery.DATA }?.reading
                        val voice = entries.firstOrNull { it.reading.query() == CubacelQuery.VOICE }?.reading
                        val sms = entries.firstOrNull { it.reading.query() == CubacelQuery.SMS }?.reading
                        val bonus = entries.firstOrNull { it.reading.query() == CubacelQuery.BONUSES_AND_USD_PLANS }?.reading
                        val enabled = !state.busy && state.lines.any { it.subscription == state.selected && it.cubacel } && state.storageReady
                        item {
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                                .background(Brush.linearGradient(listOf(Navy, Color(0xFF65468D)))).padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("SALDO PRINCIPAL", fontSize = 11.sp, letterSpacing = 1.8.sp, color = Color(0xFFDDCEED))
                                    Icon(Icons.Outlined.AccountBalanceWallet, null, tint = Mint, modifier = Modifier.size(22.dp))
                                }
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(balance?.balanceCup() ?: "—", color = Color.White, fontSize = 46.sp, lineHeight = 52.sp, fontWeight = FontWeight.SemiBold)
                                    Text(" CUP", color = Color(0xFFDDCEED), fontSize = 16.sp, modifier = Modifier.padding(bottom = 7.dp))
                                }
                                Text(balance?.let { "${freshnessLabel(it.receivedAt(), now)} · ${stamp(it.receivedAt())}" } ?: "Consulta tu línea para ver el saldo real.", color = Color(0xFFDDCEED), fontSize = 12.sp)
                                balance?.expiresAt()?.let { Text(expirationLabel(it, now), color = Color(0xFFDDCEED), fontSize = 12.sp) }
                                Button(onClick = { onQuery(CubacelQuery.BALANCE) }, enabled = enabled,
                                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Navy,
                                        disabledContainerColor = Color(0xFF705782), disabledContentColor = Color(0xFFD9CBE4)),
                                    shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(14.dp), modifier = Modifier.fillMaxWidth()) {
                                    if (state.isQuerying(CubacelQuery.BALANCE)) CircularProgressIndicator(Modifier.size(19.dp), color = Mint, strokeWidth = 2.dp)
                                    else Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(19.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (state.isQuerying(CubacelQuery.BALANCE)) "Consultando saldo…" else "Actualizar saldo", fontWeight = FontWeight.Bold)
                                }

                            }
                        }
                        item {
                            WhiteCard {
                                Button(onClick = onRefreshAll, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(8.dp))
                                    Text("Actualizar todo")
                                }
                                if (state.busy) {
                                    val slot = state.lines.firstOrNull { it.subscription == state.querySubscription }?.slot?.plus(1)
                                    Text("SIM ${slot ?: "—"} · ${state.queryNumber} de ${state.queryTotal}: ${state.activeQuery?.let { queryLabel(it) } ?: "Preparando"}", fontSize = 13.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    if (state.queryTotal > 1) {
                                        Text(if (state.stopRequested) "Se guardará la respuesta en curso y no se enviarán más consultas."
                                            else "Consultas una a una. Detener omite las pendientes; la consulta en curso puede terminar.", fontSize = 12.sp, color = Muted)
                                        OutlinedButton(onClick = { model.stopRefresh() }, enabled = !state.stopRequested) {
                                            Text(if (state.stopRequested) "Deteniendo…" else "Detener")
                                        }
                                    }
                                } else Text("Saldo, datos, minutos, SMS, bonos y recarga de la SIM seleccionada.", fontSize = 12.sp, color = Muted)
                            }
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ActionTile("Recargar", "Añade saldo", Icons.Outlined.AddCard, Color(0xFFEDE8FA), Modifier.weight(1f)) { recharge = true }
                                ActionTile("Planes", "Datos y llamadas", Icons.Outlined.ShoppingBag, Color(0xFFECE6F4), Modifier.weight(1f)) { confirmDial = "*133#" }
                            }
                        }
                        item {
                            RechargeAvailability(entries.firstOrNull { it.reading.query() == CubacelQuery.RECHARGE_STATUS }?.reading, enabled, now, state.isQuerying(CubacelQuery.RECHARGE_STATUS)) {
                                onQuery(CubacelQuery.RECHARGE_STATUS)
                            }
                        }
                        item {
                            Surface(onClick = onSyncSms, color = Color(0xFFEEE8F7), shape = RoundedCornerShape(16.dp)) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.MarkEmailRead, null, tint = Teal)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Conectar mensajes de saldo", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("Importación automática de SMS Cubacel", fontSize = 10.sp, color = Muted)
                                    }
                                    Icon(Icons.Outlined.ChevronRight, null, tint = Teal)
                                }
                            }
                        }
                        val purchased = entries.firstOrNull { it.reading.purchase() != null }?.reading
                        if (purchased != null) item {
                            val plan = purchased.purchase()!!
                            WhiteCard {
                                SectionTitle("Último plan comprado", "Recibo del operador")
                                Text(purchaseSummary(plan), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("${plan.validityDays()} días${if (plan.startsOnFirstUse()) " desde el primer uso" else " de vigencia"}. Son recursos comprados, no una lectura del consumo restante.", color = Muted, fontSize = 12.sp)
                                Text(stamp(purchased.receivedAt()), color = Muted, fontSize = 11.sp)
                            }
                        }
                        item { SectionTitle("Tus recursos", "Consulta por categoría") }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                val general = data?.bundles()?.firstOrNull { it.kind() == "general" }
                                ResourceCard("Datos", general?.let { if (it.unlimited()) "Ilimitado" else gb(it.bytes()) } ?: "—", "GB · Internet general",
                                    Icons.Outlined.DataUsage, Color(0xFFE6EEF9), Modifier.weight(1f), enabled, data, now, state.isQuerying(CubacelQuery.DATA), general?.expiresAt() ?: data?.expiresAt()) { onQuery(CubacelQuery.DATA) }
                                ResourceCard("Minutos", voice?.voiceSeconds()?.let { number(BigDecimal(it).divide(BigDecimal(60), 2, java.math.RoundingMode.DOWN)) } ?: "—",
                                    "Disponibles", Icons.Outlined.Call, Color(0xFFFFEBDD), Modifier.weight(1f), enabled, voice, now, state.isQuerying(CubacelQuery.VOICE)) { onQuery(CubacelQuery.VOICE) }
                            }
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ResourceCard("Mensajes", sms?.smsCount()?.toString() ?: "—", "SMS disponibles", Icons.Outlined.Sms,
                                    Color(0xFFEDE8FA), Modifier.weight(1f), enabled, sms, now, state.isQuerying(CubacelQuery.SMS)) { onQuery(CubacelQuery.SMS) }
                                ResourceCard("Bonos", bonus?.let { "${it.bundles().size}" } ?: "—", "Bolsas de datos", Icons.Outlined.Redeem,
                                    Color(0xFFE4F0E9), Modifier.weight(1f), enabled, bonus, now, state.isQuerying(CubacelQuery.BONUSES_AND_USD_PLANS)) { onQuery(CubacelQuery.BONUSES_AND_USD_PLANS) }
                            }
                        }
                        val bundleReadings = listOfNotNull(data, bonus)
                        if (bundleReadings.any { it.bundles().isNotEmpty() }) item {
                            WhiteCard {
                                SectionTitle("Últimos paquetes", "Cuotas separadas")
                                bundleReadings.forEach { reading -> reading.bundles().forEach { bundle ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(Modifier.weight(1f)) {
                                            Text(bundleLabel(bundle.kind()), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                            Text(bundle.expiresAt()?.let { expirationLabel(it, now) } ?: "Vigencia no informada", fontSize = 12.sp,
                                                color = if (isExpired(bundle.expiresAt(), now)) MaterialTheme.colorScheme.error else Muted)
                                            Text(freshnessLabel(reading.receivedAt(), now), fontSize = 12.sp, color = Muted)
                                        }
                                        Text(if (bundle.unlimited()) "Ilimitado" else "${gb(bundle.bytes())} GB", fontWeight = FontWeight.Bold, color = Teal, fontSize = 14.sp)
                                    }
                                } }
                            }
                        }
                        item { PrivacyFooter() }
                    } else if (page == 1) {
                        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        if (entries.isEmpty()) item {
                            WhiteCard {
                                Icon(Icons.Outlined.History, null, tint = Teal, modifier = Modifier.size(32.dp))
                                Text("Tu primera lectura empieza aquí", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("Al consultar, extraemos y guardamos los datos automáticamente. No tienes que rellenar formularios.", color = Muted, fontSize = 13.sp)
                            }
                        }
                        items(entries.take(100)) { entry ->
                            val reading = entry.reading
                            WhiteCard {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(queryLabel(reading.query()), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Icon(Icons.Outlined.Verified, "Guardado automáticamente", tint = Teal, modifier = Modifier.size(19.dp))
                                }
                                Text(readingSummary(reading), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                                Text("${reading.source()} · ${stamp(reading.receivedAt())}", fontSize = 12.sp, color = Muted)
                            }
                        }
                    } else {
                        item {
                            WhiteCard {
                                Text("Conexión y permisos", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                SettingsRow("Actualizar tarjetas SIM", Icons.Outlined.Refresh) { model.refreshLines() }
                                SettingsRow("Gestionar SIM en Android", Icons.Outlined.SimCard, onSettings)
                                SettingsRow("Permisos de la aplicación", Icons.Outlined.Security, onAppSettings)
                                SettingsRow("Sincronizar SMS de Cubacel", Icons.Outlined.MarkEmailRead, onSyncSms)
                                SettingsRow("Desactivar importación de SMS", Icons.Outlined.Unsubscribe) { model.disableSms() }
                            }
                        }
                        item {
                            WhiteCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Recordatorios locales", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                                    Switch(checked = state.remindersEnabled, onCheckedChange = onReminders,
                                        modifier = Modifier.semantics { contentDescription = "Activar recordatorios locales" })
                                }
                                Text("Avisos 24 horas antes de un vencimiento conocido y al llegar la fecha de recarga. Consulta de nuevo para confirmar tu cupo.", color = Muted, fontSize = 13.sp)
                                Text("Recursos bajos: saldo ≤ 20 CUP, Internet general ≤ 100 MB, minutos ≤ 5 o SMS ≤ 5. Solo con nuevas lecturas; no repetimos el aviso hasta que el recurso se recupere.", color = Muted, fontSize = 13.sp)
                                Text("Sin consultas automáticas. Android puede retrasar los avisos. Las compras sin fecha de inicio confirmada no generan vencimientos estimados.", color = Muted, fontSize = 12.sp)
                                if (state.remindersEnabled && !state.notificationsAllowed)
                                    Text("Android tiene bloqueadas las notificaciones. Habilítalas para recibir los recordatorios.", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                                SettingsRow("Notificaciones en Android", Icons.Outlined.Notifications, onNotificationSettings)
                            }
                        }
                        item {
                            WhiteCard {
                                Text("Privacidad, desde el inicio", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("Las respuestas USSD se procesan en este teléfono. Solo guardamos los valores reconocidos, cifrados con una clave del dispositivo. Sin cuentas, sin anuncios y sin telemetría.", color = Muted, fontSize = 13.sp)
                                SettingsRow("Reintentar almacenamiento", Icons.Outlined.Refresh) { model.retryStorage() }
                                SettingsRow("Eliminar historial local", Icons.Outlined.DeleteOutline) { delete = true }
                                Text("La SIM y el historial se asocian al registro local de Android. Si sustituyes una tarjeta, elimina su historial para evitar mezclar lecturas.", fontSize = 11.sp, color = Muted)
                            }
                        }
                        item {
                            Text("SIMple 0.2 · Kotlin + Compose", fontSize = 12.sp, color = Muted)
                            Text("Las consultas necesitan una SIM Cubacel y cobertura. Los SMS del operador se importan solo si autorizas los permisos. Los anuncios y mensajes personales no se guardan.", fontSize = 12.sp, color = Muted)
                        }
                    }
                }
            }
        }
        if (recharge) AlertDialog(onDismissRequest = { recharge = false }, title = { Text("Recarga tu línea") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Elige cómo continuar. Confirma la SIM y cualquier pago en la aplicación que se abra.", fontSize = 13.sp)
                OutlinedButton(onClick = { recharge = false; confirmDial = "*666" }, modifier = Modifier.fillMaxWidth()) { Text("Usar tarjeta de recarga") }
                OutlinedButton(onClick = { recharge = false; onRechargeSite() }, modifier = Modifier.fillMaxWidth()) { Text("Vías oficiales de ETECSA") }
            } }, confirmButton = { TextButton(onClick = { recharge = false }) { Text("Cerrar") } })
        confirmDial?.let { code -> AlertDialog(onDismissRequest = { confirmDial = null }, title = { Text("Continuar en Teléfono") },
            text = { Text("Se abrirá $code en el marcador. Elige tu SIM Cubacel y confirma allí. SIMple no realiza compras ni recargas automáticamente.") },
            confirmButton = { TextButton(onClick = { confirmDial = null; onDial(code) }) { Text("Abrir marcador") } },
            dismissButton = { TextButton(onClick = { confirmDial = null }) { Text("Cancelar") } }) }
        if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("¿Eliminar el historial?") },
            text = { Text("Se borrarán todas las lecturas y se desactivará la importación automática de SMS. No se puede deshacer.") },
            confirmButton = { TextButton(onClick = { delete = false; model.clearHistory() }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { delete = false }) { Text("Cancelar") } })
    }
}

@Composable private fun RechargeAvailability(reading: CarrierReading?, enabled: Boolean, now: Long, querying: Boolean, query: () -> Unit) {
    val available = reading?.availableRechargeCup()?.let { runCatching { BigDecimal(it) }.getOrNull() }
    val next = reading?.nextRechargeAt()
    val datePassed = next != null && now >= next
    val title = when {
        available != null && available.signum() > 0 -> "Puedes recargar hasta ${number(available)} CUP"
        datePassed -> "La fecha indicada ya llegó"
        next != null -> "Disponible desde ${rechargeDate(next)}"
        available?.signum() == 0 -> "Cupo nacional agotado"
        else -> "¿Cuándo puedes recargar?"
    }
    WhiteCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).background(Color(0xFFEEE8F7), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.EventAvailable, null, tint = Teal, modifier = Modifier.size(23.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("RECARGA NACIONAL", fontSize = 10.sp, letterSpacing = 1.sp, color = Muted)
                Text("Tu próximo cupo", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(title, fontSize = 22.sp, lineHeight = 28.sp, color = Teal, fontWeight = FontWeight.Bold)
        Text(when {
            datePassed -> "Actualiza para confirmar cuánto puedes recargar hoy. La fecha por sí sola no confirma el cupo."
            next != null -> "El operador indica cuándo vuelve a estar disponible. Esta fecha no es el vencimiento de tu línea."
            reading == null -> "Consulta el límite y la fecha de reapertura de tu recarga en CUP."
            else -> "Según la última respuesta del operador. Consulta de nuevo antes de recargar."
        }, fontSize = 12.sp, color = Muted)
        if (available != null) Text("Cupo en la última consulta: ${number(available)} CUP", fontSize = 12.sp, fontWeight = FontWeight.Medium)
        reading?.receivedAt()?.let { Text("${freshnessLabel(it, now)} · ${stamp(it)}", color = Muted, fontSize = 10.sp) }
        OutlinedButton(onClick = query, enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            if (querying) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
            else Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (querying) "Consultando recarga…" else "Actualizar disponibilidad")
        }
    }
}
private fun rechargeDate(at: Long) = SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es"))
    .apply { timeZone = java.util.TimeZone.getTimeZone("America/Havana") }.format(Date(at))

@Composable private fun WhiteCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}
@Composable private fun SectionTitle(title: String, detail: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(detail, fontSize = 10.sp, color = Muted)
    }
}
@Composable private fun ActionTile(title: String, detail: String, icon: ImageVector, color: Color, modifier: Modifier, action: () -> Unit) {
    Surface(onClick = action, color = color, shape = RoundedCornerShape(18.dp), modifier = modifier) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Ink, modifier = Modifier.size(23.dp)); Spacer(Modifier.width(10.dp))
            Column { Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold); Text(detail, fontSize = 10.sp, color = Muted) }
        }
    }
}
@Composable private fun ResourceCard(title: String, value: String, unit: String, icon: ImageVector, tint: Color, modifier: Modifier, enabled: Boolean, reading: CarrierReading?, now: Long, querying: Boolean, expiry: Long? = reading?.expiresAt(), action: () -> Unit) {
    Surface(onClick = action, enabled = enabled, shape = RoundedCornerShape(21.dp), color = Color.White, modifier = modifier) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).background(tint, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Ink, modifier = Modifier.size(19.dp)) }
                if (querying) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.NorthEast, "Consultar $title", tint = Muted, modifier = Modifier.size(16.dp))
            }
            Text(if (querying) "Consultando $title…" else title, fontSize = 12.sp, color = Muted)
            Text(value, fontSize = if (value.length > 8) 20.sp else 29.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(unit, fontSize = 10.sp, color = Muted)
            Text(reading?.let { freshnessLabel(it.receivedAt(), now) } ?: "Sin consultas", fontSize = 12.sp, color = Muted)
            reading?.let { Text(stamp(it.receivedAt()), fontSize = 11.sp, color = Muted) }
            expiry?.let { Text(expirationLabel(it, now), fontSize = 12.sp,
                color = if (isExpired(it, now)) MaterialTheme.colorScheme.error else Muted) }
        }
    }
}
@Composable private fun SettingsRow(label: String, icon: ImageVector, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = action).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Teal, modifier = Modifier.size(21.dp)); Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f), fontSize = 13.sp); Icon(Icons.Outlined.ChevronRight, null, tint = Muted)
    }
}
@Composable private fun PrivacyFooter() {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Lock, null, tint = Muted, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(6.dp))
        Text("Guardado automático · Cifrado en tu dispositivo", color = Muted, fontSize = 10.sp)
    }
}
private fun stamp(at: Long) = SimpleDateFormat("d MMM · HH:mm", Locale("es")).format(Date(at))
private fun expiryLabel(at: Long) = SimpleDateFormat("d MMM yyyy", Locale("es")).apply { timeZone = java.util.TimeZone.getTimeZone("America/Havana") }.format(Date(at - 1))
private fun number(value: BigDecimal) = value.stripTrailingZeros().toPlainString()
private fun gb(bytes: Long?) = bytes?.let { number(BigDecimal(it).divide(BigDecimal(1_000_000_000), 3, java.math.RoundingMode.DOWN)) } ?: "—"
private fun bundleLabel(kind: String) = when(kind) { "general" -> "Internet · todas las redes"; "national" -> "Navegación nacional"; "lte" -> "Solo LTE / 4G"; "night" -> "Navegación nocturna"; "mail" -> "Correo nauta"; "todus" -> "toDus"; "whatsapp" -> "WhatsApp"; "facebook" -> "Facebook"; else -> "Datos" }
private fun queryLabel(query: CubacelQuery) = when(query) { CubacelQuery.BALANCE -> "Saldo principal"; CubacelQuery.DATA -> "Datos móviles"; CubacelQuery.VOICE -> "Minutos"; CubacelQuery.SMS -> "Mensajes"; CubacelQuery.BONUSES_AND_USD_PLANS -> "Bonos y planes USD"; CubacelQuery.RECHARGE_STATUS -> "Recargas nacionales" }
private fun readingSummary(r: CarrierReading): String = when(r.query()) {
    CubacelQuery.BALANCE -> r.balanceCup()?.let { "$it CUP" } ?: r.purchase()?.let { "Plan comprado: ${purchaseSummary(it)}" } ?: "Recarga recibida: ${r.receivedRechargeCup()} CUP"
    CubacelQuery.VOICE -> "${r.voiceSeconds()?.div(60)} min"
    CubacelQuery.SMS -> "${r.smsCount()} SMS"
    CubacelQuery.RECHARGE_STATUS -> r.nextRechargeAt()?.let { "Recarga disponible desde ${rechargeDate(it)}" } ?: "${r.availableRechargeCup() ?: "—"} CUP disponibles para recargar"
    else -> r.bundles().joinToString(" · ") { "${bundleLabel(it.kind())}: ${if (it.unlimited()) "Ilimitado" else gb(it.bytes()) + " GB"}" }
}

private fun purchaseSummary(plan: CarrierReading.Purchase) = listOfNotNull(
    plan.totalBytes()?.let { "${gb(it)} GB" }, plan.lteBytes()?.let { "${gb(it)} GB LTE" },
    plan.voiceSeconds()?.let { "${it / 60} min" }, plan.smsCount()?.let { "$it SMS" }
).joinToString(" · ")

private fun expirationLabel(at: Long, now: Long) =
    if (isExpired(at, now)) "Vencido · ${expiryLabel(at)}" else "Vigente hasta ${expiryLabel(at)}"
