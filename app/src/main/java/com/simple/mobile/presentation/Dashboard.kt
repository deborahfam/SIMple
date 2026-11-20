@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.simple.mobile.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simple.mobile.domain.carrier.CubacelQuery
import com.simple.mobile.domain.model.CarrierReading
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFF1EAF9) else Color(0xFF29223D)
private val Muted: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFC6BDCF) else Color(0xFF655C73)
private val Paper: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF17131E) else Color(0xFFF7F5FC)
private val Mint = Color(0xFFE0D4FF)
private val Teal: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFD1B8FF) else Color(0xFF7353B6)
private val Navy = Color(0xFF35234F)

@Composable fun Dashboard(model: DashboardViewModel, updates: com.simple.mobile.data.updates.AppUpdates, onInstallUpdate: () -> Unit, onConnect: () -> Unit,
    onQuery: (CubacelQuery) -> Unit, onRefreshAll: () -> Unit, onRefreshNeeded: () -> Unit, onRetryQueries: () -> Unit, onRescanSms: () -> Unit, onReminders: (Boolean) -> Unit, onNotificationSettings: () -> Unit, onSmsEnabled: (Boolean) -> Unit, onSyncSms: () -> Unit, onSettings: () -> Unit, onAppSettings: () -> Unit,
    onPickContact: () -> Unit,
    onTransfer: (com.simple.mobile.domain.transfer.TransferDraft) -> Boolean) {
    val state by model.state.collectAsStateWithLifecycle()
    val update by updates.state.collectAsStateWithLifecycle()
    val automaticUpdates by updates.automatic.collectAsStateWithLifecycle()
    val entries = remember(state.entries, state.selected) { state.entries.filter { it.subscription == state.selected } }
    val stacked = LocalConfiguration.current.screenWidthDp < 360 || LocalDensity.current.fontScale > 1.2f
    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); kotlinx.coroutines.delay(30_000) }
    }
    var historyTransfers by rememberSaveable { mutableStateOf(false) }
    var selectedReading by remember { mutableStateOf<CarrierReading?>(null) }
    LaunchedEffect(state.selected) { selectedReading = null }
    var historyCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var historyDays by rememberSaveable { mutableIntStateOf(0) }
    var historyLimit by rememberSaveable { mutableIntStateOf(25) }
    val filteredHistory = remember(entries, historyCategory, historyDays, now) { HistoryFilter.apply(entries, historyCategory, historyDays, now) }
    LaunchedEffect(state.selected, historyCategory, historyDays) { historyLimit = 25 }
    var page by rememberSaveable { mutableIntStateOf(0) }
    val homeScroll = rememberLazyListState()
    val historyScroll = rememberLazyListState()
    val statisticsScroll = rememberLazyListState()
    val settingsScroll = rememberLazyListState()
    var showPackages by rememberSaveable(state.selected) { mutableStateOf(false) }
    var rechargeOpen by rememberSaveable { mutableStateOf(false) }
    val onRecharge = { rechargeOpen = true }
    var buyPlans by rememberSaveable { mutableStateOf(false) }
    var transfer by rememberSaveable { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var deleteSubscription by remember { mutableStateOf<Int?>(null) }
    var deleteLabel by remember { mutableStateOf("") }
    var rename by remember { mutableStateOf<Line?>(null) }
    var nickname by remember { mutableStateOf("") }
    var linePhone by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); model.dismissMessage() } }
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme(primary = Teal, background = Paper, surface = Color(0xFF241F2E), secondaryContainer = Color(0xFF383044), onSecondaryContainer = Ink, outlineVariant = Color(0xFF40374D), onSurface = Ink, onBackground = Ink) else lightColorScheme(primary = Teal, secondary = Navy, background = Paper, surface = Color.White, secondaryContainer = Color(0xFFEEE8F7), onSecondaryContainer = Ink, outlineVariant = Color(0xFFE8E2EF),
        onSurface = Ink, onBackground = Ink)) {
        Scaffold(containerColor = Paper, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                Surface(Modifier.widthIn(max = 600.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                    val scrollTabs = LocalDensity.current.fontScale > 1.3f || LocalConfiguration.current.screenWidthDp < 320
                    Row(Modifier.selectableGroup().then(if (scrollTabs) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                        .padding(horizontal = 4.dp, vertical = 6.dp)) {
                        listOf("Inicio" to Icons.Outlined.SpaceDashboard, "Historial" to Icons.Outlined.History,
                            "Ajustes" to Icons.Outlined.Tune, "Estadísticas" to Icons.Outlined.BarChart).forEachIndexed { index, item ->
                            val active = page == index
                            Column((if (scrollTabs) Modifier.width((100 * LocalDensity.current.fontScale).dp) else Modifier.weight(1f))
                                .clip(RoundedCornerShape(12.dp))
                                .selectable(selected = active, role = Role.Tab, onClick = { page = index })
                                .heightIn(min = 64.dp).padding(horizontal = 2.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.width(20.dp).height(3.dp).clip(CircleShape)
                                    .background(if (active) Teal else Color.Transparent))
                                Icon(item.second, null, tint = if (active) Teal else Muted, modifier = Modifier.size(24.dp))
                                Text(item.first, color = if (active) Teal else Muted, fontSize = 12.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                LazyColumn(Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                    state = when (page) { 1 -> historyScroll; 2 -> settingsScroll; 3 -> statisticsScroll; else -> homeScroll },
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.SimCard, null, tint = Teal, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("SIMple", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Outlined.VerifiedUser, "Datos cifrados en este dispositivo", tint = Muted, modifier = Modifier.size(22.dp))
                        }
                    }
                    if (page != 0) item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(when (page) { 1 -> "Tu historial"; 2 -> "A tu manera"; 3 -> "Tus estadísticas"; else -> "Tu línea, de un vistazo" },
                                fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, color = Ink,
                                modifier = Modifier.semantics { heading() })
                            Text(when (page) {
                                1 -> "Revisa tus consultas y mensajes guardados."
                                2 -> "Conexiones, avisos y privacidad."
                                3 -> "Consumo, presupuesto y próxima recarga."
                                else -> "Saldo y recursos de tu SIM Cubacel."
                            }, fontSize = 14.sp, lineHeight = 20.sp, color = Muted)
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        }
                        if (state.displayLines.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(state.displayLines, key = { it.subscription }) { line ->
                                    val selected = state.selected == line.subscription
                                    Surface(onClick = { model.select(line.subscription) },
                                        modifier = Modifier.semantics { this.selected = selected },
                                        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.outlineVariant),
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) {
                                        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Outlined.SimCard, null, tint = if (selected) Teal else Muted, modifier = Modifier.size(21.dp))
                                            Spacer(Modifier.width(10.dp))
                                            Column {
                                                Text(line.label, color = if (selected) Teal else Ink,
                                                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                if (!line.active || !line.cubacel) Text(if (!line.active) "Historial guardado" else "Operador sin integración", color = Muted, fontSize = 12.sp)
                                            }
                                            if (selected) { Spacer(Modifier.width(12.dp)); Icon(Icons.Outlined.CheckCircle, null, tint = Teal, modifier = Modifier.size(17.dp)) }
                                        }
                                    }
                                }
                                item { OutlinedIconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.Add, "Añadir o activar SIM") } }
                            }
                        }
                    }
                    }
                    if (page == 2) state.displayLines.firstOrNull { it.subscription == state.selected }?.let { line ->
                        item {
                            WhiteCard {
                                Text(line.label, fontWeight = FontWeight.Bold)
                                if (!line.active) Text("Línea guardada: puedes revisar sus lecturas sin tener la SIM activa. Las consultas están deshabilitadas.", color = Muted, fontSize = 13.sp)
                                OutlinedButton(onClick = { rename = line; nickname = line.nickname; linePhone = state.linePhones[line.subscription].orEmpty() }, enabled = state.storageReady && !state.managingLine) { Text("Editar línea") }
                                TextButton(onClick = { deleteSubscription = line.subscription; deleteLabel = line.label; delete = true },
                                    enabled = state.storageReady && !state.busy && !state.managingLine && !state.transferSaving) { Text("Eliminar datos de esta línea") }
                            }
                        }
                    }
                    state.refreshOffer?.let { subscription ->
                        item {
                            WhiteCard {
                                Text("¿Actualizamos tus recursos?", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("Volviste del servicio externo. Consultar el saldo no confirma por sí solo una compra o transferencia.", color = Muted)
                                Text(state.displayLines.firstOrNull { it.subscription == subscription }?.label ?: "La línea ya no está disponible.")
                                Button(onClick = { model.select(subscription); model.dismissRefreshOffer(); onRefreshAll() },
                                    enabled = state.storageReady && !state.busy && !state.managingLine && state.lines.any { it.subscription == subscription && it.cubacel }) { Text("Actualizar esta línea") }
                                TextButton(onClick = { model.dismissRefreshOffer() }) { Text("Ahora no") }
                            }
                        }
                    }
                    if (state.unreadableCount > 0) item {
                        WhiteCard {
                            Text("${state.unreadableCount} lecturas no se pudieron abrir", fontWeight = FontWeight.Bold)
                            Text("Mostramos las lecturas legibles. Los registros afectados siguen cifrados y conservados; algunos valores pueden ser anteriores. Reintenta para comprobar si vuelven a estar disponibles.")
                            OutlinedButton(onClick = { model.retryStorage() }) { Text("Reintentar lectura") }
                        }
                    }
                    if (page != 2 && update.release != null) item {
                        WhiteCard {
                            Text("Hay una nueva versión de SIMple", fontWeight = FontWeight.Bold)
                            Text(update.message)
                            Button(onClick = { page = 2 }) { Text("Ver actualización") }
                        }
                    }
                    if (page == 0) state.displayLines.filter { it.active }.forEach { line ->
                        val date = state.rechargeDates[line.subscription] ?: StatisticsPolicy.recharge(state.entries, line.subscription, now)
                        if (date == StatisticsPolicy.date(now)) item {
                            WhiteCard {
                                Text("${line.label} · hoy es tu fecha de recarga", fontWeight = FontWeight.Bold)
                                Text("Revisa tus recursos y confirma la disponibilidad antes de recargar.")
                                TextButton(onClick = { model.select(line.subscription); page = 3 }) { Text("Ver presupuesto de esta SIM") }
                            }
                        }
                    }
                    if (page == 0) {
                        val balance = entries.firstOrNull { it.reading.query() == CubacelQuery.BALANCE && it.reading.balanceCup() != null }?.reading
                        val data = entries.firstOrNull { it.reading.query() == CubacelQuery.DATA }?.reading
                        val voice = entries.firstOrNull { it.reading.query() == CubacelQuery.VOICE }?.reading
                        val sms = entries.firstOrNull { it.reading.query() == CubacelQuery.SMS }?.reading
                        val bonus = entries.firstOrNull { it.reading.query() == CubacelQuery.BONUSES_AND_USD_PLANS }?.reading
                        val selectedLine = state.displayLines.firstOrNull { it.subscription == state.selected }
                        val enabled = !state.busy && !state.managingLine && state.lines.any { it.subscription == state.selected && it.cubacel } && state.storageReady
                        item {
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                                .background(Navy).padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Saldo disponible", fontSize = 13.sp, color = Color(0xFFDDCEED))
                                        FlowRow(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(balance?.balanceCup() ?: "—", color = Color.White,
                                                fontSize = if ((balance?.balanceCup()?.length ?: 0) > 8) 30.sp else 38.sp,
                                                lineHeight = 44.sp, fontWeight = FontWeight.SemiBold)
                                            Text(" CUP", color = Color(0xFFDDCEED), fontSize = 14.sp,
                                                modifier = Modifier.align(Alignment.Bottom).padding(bottom = 5.dp))
                                        }
                                    }
                                    IconButton(onClick = { onQuery(CubacelQuery.BALANCE) }, enabled = enabled,
                                        modifier = Modifier.size(48.dp).background(Color.White.copy(alpha = 0.07f), CircleShape)) {
                                        Icon(Icons.Outlined.Refresh, "Actualizar solo el saldo", tint = Mint, modifier = Modifier.size(21.dp))
                                    }
                                }
                                Text(balance?.let { freshnessLabel(it.receivedAt(), now) } ?: when {
                                    !state.permission -> "Conecta tu SIM para consultar el saldo."
                                    selectedLine?.active == false -> "Línea guardada. Activa esta SIM para consultar."
                                    selectedLine != null && !selectedLine.cubacel -> "Las consultas están disponibles para SIM Cubacel."
                                    selectedLine == null -> "Añade o activa una SIM Cubacel para consultar."
                                    else -> "Consulta tu línea para ver el saldo real."
                                }, color = Color(0xFFDDCEED), fontSize = 12.sp)
                                balance?.expiresAt()?.let { Text(expirationLabel(it, now), color = Color(0xFFDDCEED), fontSize = 12.sp) }
                                HorizontalDivider(color = Color.White.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                                val general = data?.bundles()?.firstOrNull { it.kind() == "general" }
                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        SimResource("Internet", general?.let { if (it.unlimited()) "Ilimitado" else "${gb(it.bytes())} GB" } ?: "—",
                                            Icons.Outlined.DataUsage, enabled, data, now, state.isQuerying(CubacelQuery.DATA),
                                            expiry = general?.expiresAt() ?: data?.expiresAt(), modifier = Modifier.weight(1f).fillMaxHeight()) { onQuery(CubacelQuery.DATA) }
                                        SimResource("Minutos", voice?.voiceSeconds()?.let { "${number(BigDecimal(it).divide(BigDecimal(60), 2, java.math.RoundingMode.DOWN))} min" } ?: "—",
                                            Icons.Outlined.Call, enabled, voice, now, state.isQuerying(CubacelQuery.VOICE),
                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                            note = voice?.voiceSeconds()?.let { "≈ ${number(BigDecimal(it).divide(BigDecimal(3600), 1, java.math.RoundingMode.HALF_UP))} h" }) { onQuery(CubacelQuery.VOICE) }
                                    }
                                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        SimResource("Mensajes", sms?.smsCount()?.let { "$it SMS" } ?: "—", Icons.Outlined.Sms,
                                            enabled, sms, now, state.isQuerying(CubacelQuery.SMS), modifier = Modifier.weight(1f).fillMaxHeight()) { onQuery(CubacelQuery.SMS) }
                                        SimResource("Bonos", bonus?.let { "${it.bundles().size} bolsas" } ?: "—", Icons.Outlined.Redeem,
                                            enabled, bonus, now, state.isQuerying(CubacelQuery.BONUSES_AND_USD_PLANS), modifier = Modifier.weight(1f).fillMaxHeight()) { onQuery(CubacelQuery.BONUSES_AND_USD_PLANS) }
                                    }
                                }
                                val bundleReadings = listOfNotNull(data, bonus)
                                run {
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                                    TextButton(onClick = { showPackages = !showPackages }, modifier = Modifier.fillMaxWidth().semantics { stateDescription = if (showPackages) "Expandido" else "Contraído" },
                                        colors = ButtonDefaults.textButtonColors(contentColor = Mint)) {
                                        Text("Paquetes y lecturas", modifier = Modifier.weight(1f))
                                        Icon(if (showPackages) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                                    }
                                    if (showPackages) {
                                        Text("Toca un recurso para actualizarlo. La actualización rápida omite lecturas válidas de los últimos 15 minutos.", color = Mint, fontSize = 12.sp)
                                        TextButton(onClick = onRefreshAll, enabled = enabled) { Text("Actualizar todo de nuevo", color = Mint) }
                                        listOf("Internet general" to data, "Minutos" to voice, "Mensajes" to sms, "Bonos" to bonus).forEach { (label, reading) ->
                                            Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                                Text(reading?.let { "${freshnessLabel(it.receivedAt(), now)} · ${stamp(it.receivedAt())}" } ?: "Sin lectura. Toca el recurso para consultarlo.",
                                                    color = Color(0xFFDDCEED), fontSize = 12.sp)
                                                reading?.expiresAt()?.let { Text(expirationLabel(it, now), fontSize = 12.sp,
                                                    color = if (isExpired(it, now)) Color(0xFFFFC4BA) else Color(0xFFDDCEED)) }
                                            }
                                        }
                                        if (bundleReadings.any { it.bundles().isNotEmpty() }) Text("Paquetes de datos", color = Mint, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    if (showPackages) bundleReadings.forEach { reading -> reading.bundles().forEach { bundle ->
                                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(bundleLabel(bundle.kind()), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                            Text(if (bundle.unlimited()) "Ilimitado" else "${gb(bundle.bytes())} GB", fontWeight = FontWeight.Bold, color = Mint, fontSize = 18.sp)
                                            Text(bundle.expiresAt()?.let { expirationLabel(it, now) } ?: "Vigencia no informada", fontSize = 12.sp,
                                                color = if (isExpired(bundle.expiresAt(), now)) Color(0xFFFFC4BA) else Color(0xFFDDCEED))
                                            Text(freshnessLabel(reading.receivedAt(), now), fontSize = 12.sp, color = Color(0xFFDDCEED))
                                        }
                                    } }
                                }
                                Button(onClick = onRefreshNeeded, enabled = enabled,
                                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Navy,
                                        disabledContainerColor = Color(0xFF705782), disabledContentColor = Color(0xFFD9CBE4)),
                                    shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(14.dp), modifier = Modifier.fillMaxWidth()) {
                                    if (state.busy) CircularProgressIndicator(Modifier.size(19.dp), color = Color(0xFFD9CBE4), strokeWidth = 2.dp)
                                    else Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(19.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (state.busy) "Actualizando…" else "Actualizar", fontWeight = FontWeight.Bold)
                                }
                                val pending = state.pendingQueries[state.selected].orEmpty()
                                if (pending.isNotEmpty() && !state.busy) OutlinedButton(onClick = onRetryQueries, enabled = enabled) {
                                    Text("Reintentar pendientes (${pending.size})", color = Mint)
                                }
                            }
                        }
                        if (state.busy) item {
                            WhiteCard {
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
                                }
                            }
                        }
                        item {
                            FlowRow(maxItemsInEachRow = if (stacked) 1 else 3, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                QuickAction("Recargar", Icons.Outlined.AddCard, Modifier.weight(1f)) { onRecharge() }
                                QuickAction("Comprar planes", Icons.Outlined.ShoppingBag, Modifier.weight(1f)) { buyPlans = true }
                                QuickAction("Transferir", Icons.Outlined.Send, Modifier.weight(1f)) { transfer = true }
                            }
                        }
                        item {
                            AttentionCard(DashboardInsights.notices(state.entries, state.selected, now), enabled, now,
                                onQuery, onRefreshAll, { onRecharge() }, { buyPlans = true })
                        }
                        item {
                            RechargeAvailability(entries.firstOrNull { it.reading.query() == CubacelQuery.RECHARGE_STATUS }?.reading, enabled, now, state.isQuerying(CubacelQuery.RECHARGE_STATUS)) {
                                onQuery(CubacelQuery.RECHARGE_STATUS)
                            }
                        }
                    } else if (page == 1) {
                        item {
                            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Lecturas", "Transferencias").forEachIndexed { index, label ->
                                    val active = historyTransfers == (index == 1)
                                    Surface(color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) {
                                        Box(Modifier.selectable(selected = active, role = Role.Tab, onClick = { historyTransfers = index == 1 })
                                            .heightIn(min = 48.dp).padding(12.dp), contentAlignment = Alignment.Center) {
                                            Text(label, color = if (active) Teal else Muted, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                        if (historyTransfers) {
                            item { TransferHistory(state.transfers.filter { it.subscription == state.selected }, state.transferSaving || state.managingLine, model::transferResult) }
                        } else {
                            val filtered = filteredHistory
                            item {
                                WhiteCard {
                                    DetailDisclosure("Filtrar · ${historyCategory?.let { queryLabel(CubacelQuery.valueOf(it)) } ?: "Todas"} · ${if (historyDays == 0) "Todo el período" else if (historyDays == 1) "24 horas" else "$historyDays días"}") {
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            item { FilterChip(selected = historyCategory == null, onClick = { historyCategory = null }, label = { Text("Todas") }) }
                                            items(CubacelQuery.values().toList()) { query ->
                                                FilterChip(selected = historyCategory == query.name, onClick = { historyCategory = query.name }, label = { Text(queryLabel(query)) })
                                            }
                                        }
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(listOf(0, 1, 7, 30)) { days ->
                                                FilterChip(selected = historyDays == days, onClick = { historyDays = days }, label = { Text(if (days == 0) "Todo" else if (days == 1) "24 horas" else "$days días") })
                                            }
                                        }
                                    }
                                    Text("${filtered.size} lecturas", fontSize = 12.sp, color = Muted)
                                }
                            }
                            if (entries.isNotEmpty() && filtered.isEmpty()) item {
                                WhiteCard {
                                    Icon(Icons.Outlined.SearchOff, null, tint = Teal, modifier = Modifier.size(32.dp))
                                    Text("No hay resultados", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text("Prueba otra categoría o amplía el período.", color = Muted)
                                    TextButton(onClick = { historyCategory = null; historyDays = 0 }) { Text("Mostrar todas las lecturas") }
                                }
                            }
                            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                            if (entries.isEmpty()) item {
                                WhiteCard {
                                    Icon(Icons.Outlined.History, null, tint = Teal, modifier = Modifier.size(32.dp))
                                    Text("Tu primera lectura empieza aquí", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Text("Al consultar tu línea, las lecturas se guardan aquí automáticamente.", color = Muted, fontSize = 14.sp)
                                    Button(onClick = { page = 0 }) { Text("Ir a mi línea") }
                                }
                            }
                            filtered.take(historyLimit).groupBy { historyDate(it.reading.receivedAt()) }.forEach { (date, records) ->
                                item {
                                    Text(date, style = MaterialTheme.typography.labelLarge, color = Muted,
                                        modifier = Modifier.padding(top = 4.dp).semantics { heading() })
                                }
                                items(records) { entry ->
                                    val reading = entry.reading
                                    Surface(onClick = { selectedReading = reading }, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape) {
                                                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                                    Icon(readingIcon(reading.query()), null, tint = Teal, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(readingTitle(reading), style = MaterialTheme.typography.labelLarge, color = Muted)
                                                Text(readingSummary(reading), style = MaterialTheme.typography.titleMedium,
                                                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                                Text(SimpleDateFormat("HH:mm", Locale.forLanguageTag("es")).format(Date(reading.receivedAt())),
                                                    style = MaterialTheme.typography.bodySmall, color = Muted)
                                            }
                                            Icon(Icons.Outlined.ChevronRight, "Ver detalle", tint = Muted, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                            if (filtered.size > historyLimit) item {
                                OutlinedButton(onClick = { historyLimit += 25 }, modifier = Modifier.fillMaxWidth()) { Text("Cargar más") }
                            }
                        }
                    } else if (page == 3) {
                        item {
                            UsageTrendsCard(DashboardInsights.trends(state.entries, state.selected, now),
                                !state.busy && !state.managingLine && state.storageReady && state.lines.any { it.subscription == state.selected && it.cubacel },
                                onRefreshAll, { page = 1; historyTransfers = false })
                        }
                        item { Statistics(state, model, now, onReminders, onNotificationSettings, onRefreshAll) }
                    } else {
                        item {
                            WhiteCard {
                                Text("Conexión y permisos", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                SettingsRow("Destinatarios y transferencias", Icons.Outlined.PeopleOutline) { transfer = true }
                                SettingsRow("Actualizar tarjetas SIM", Icons.Outlined.Refresh) { model.refreshLines() }
                                SettingsRow("Gestionar SIM en Android", Icons.Outlined.SimCard, onSettings)
                                SettingsRow("Permisos de la aplicación", Icons.Outlined.Security, onAppSettings)
                            }
                        }
                        if (com.simple.mobile.BuildConfig.DIRECT_DISTRIBUTION) item { SmsConnectionCard(state, onSmsEnabled, onSyncSms, onAppSettings, onRescanSms) }
                        item {
                            WhiteCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Recordatorios locales", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                                    Switch(checked = state.remindersEnabled, onCheckedChange = onReminders,
                                        modifier = Modifier.semantics { contentDescription = "Activar recordatorios locales" })
                                }
                                Text("Avisos 24 horas antes de un vencimiento conocido y al llegar la fecha de recarga. Consulta de nuevo para confirmar tu cupo.", color = Muted, fontSize = 13.sp)
                                DetailDisclosure("Cuándo se envían los avisos") {
                                    Text("Recursos bajos: saldo ≤ 20 CUP, Internet general ≤ 100 MB, minutos ≤ 5 o SMS ≤ 5. Solo con nuevas lecturas; no repetimos el aviso hasta que el recurso se recupere.", color = Muted, fontSize = 13.sp)
                                    Text("Sin consultas automáticas. Android puede retrasar los avisos. Las compras sin fecha de inicio confirmada no generan vencimientos estimados.", color = Muted, fontSize = 12.sp)
                                }
                                if (state.remindersEnabled && !state.notificationsAllowed)
                                    Text("Android tiene bloqueadas las notificaciones. Habilítalas para recibir los recordatorios.", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                                SettingsRow("Notificaciones en Android", Icons.Outlined.Notifications, onNotificationSettings)
                            }
                        }
                        item {
                            WhiteCard {
                                Text("Privacidad, desde el inicio", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("Datos cifrados en este dispositivo. Sin cuentas ni telemetría.", color = Muted, fontSize = 13.sp)
                                DetailDisclosure("Cómo protegemos tus datos") {
                                    Text("Las respuestas USSD se procesan en este teléfono. Solo guardamos los valores reconocidos, cifrados con una clave del dispositivo. Sin cuentas, sin anuncios y sin telemetría.", color = Muted, fontSize = 13.sp)
                                }
                                SettingsRow("Reintentar almacenamiento", Icons.Outlined.Refresh) { model.retryStorage() }
                                SettingsRow("Eliminar datos de todas las líneas", Icons.Outlined.DeleteOutline) { deleteSubscription = null; deleteLabel = "todas las líneas"; delete = true }
                                Text("La SIM y el historial se asocian al registro local de Android. Si sustituyes una tarjeta, elimina su historial para evitar mezclar lecturas.", fontSize = 11.sp, color = Muted)
                            }
                        }
                        if (com.simple.mobile.BuildConfig.DIRECT_DISTRIBUTION) item {
                            WhiteCard {
                                Text("Actualizar SIMple", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("Versión ${com.simple.mobile.BuildConfig.VERSION_NAME} (${com.simple.mobile.BuildConfig.VERSION_CODE})", color = Muted)
                                Text("Consulta GitHub y descarga el APK usando Internet. Android te pedirá confirmar la instalación.", fontSize = 13.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Avisar de nuevas versiones al abrir", modifier = Modifier.weight(1f))
                                    Switch(checked = automaticUpdates, onCheckedChange = { updates.setAutomatic(it) })
                                }
                                Text("Opcional: consulta GitHub como máximo una vez al día al abrir la app. No descarga ni instala automáticamente.", fontSize = 13.sp)
                                Text(update.message, fontSize = 13.sp)
                                if (update.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                                OutlinedButton(onClick = { updates.check() }, enabled = !update.busy) { Text("Buscar actualización") }
                                if (update.release != null && !update.ready) Button(onClick = { updates.download() }, enabled = !update.busy) { Text("Descargar actualización") }
                                if (update.ready) Button(onClick = onInstallUpdate, enabled = !update.busy) { Text("Instalar actualización") }
                            }
                        }
                        item { AboutCard() }
                        item {
                            Text("SIMple ${com.simple.mobile.BuildConfig.VERSION_NAME}", fontSize = 12.sp, color = Muted)
                            Text("Las consultas necesitan una SIM Cubacel y cobertura. Consulta los SMS de confirmación en tu aplicación de mensajes.", fontSize = 12.sp, color = Muted)
                        }
                    }
                }
            }
        }
        selectedReading?.let { reading ->
            AlertDialog(onDismissRequest = { selectedReading = null }, title = { Text(readingTitle(reading)) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(readingSummary(reading), style = MaterialTheme.typography.titleLarge)
                        Text(SimpleDateFormat("d MMM yyyy · HH:mm", Locale.forLanguageTag("es")).format(Date(reading.receivedAt())), color = Muted)
                        Text("Origen: ${reading.source()}", style = MaterialTheme.typography.bodySmall)
                        reading.expiresAt()?.let { Text(expirationLabel(it, now)) }
                        reading.bundles().forEach { bundle ->
                            bundle.expiresAt()?.let { Text("${bundleLabel(bundle.kind())}: ${expirationLabel(it, now)}") }
                        }
                        reading.purchase()?.let { purchase ->
                            Text("${purchase.validityDays()} días${if (purchase.startsOnFirstUse()) " desde el primer uso" else " de vigencia"}. Recursos comprados, no saldo restante.")
                        }
                        Text("Lectura guardada en ese momento; no representa necesariamente tus recursos actuales.",
                            style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }, confirmButton = { TextButton(onClick = { selectedReading = null }) { Text("Cerrar") } })
        }
        if (transfer) TransferPanel(state, model, onPickContact, onTransfer) { transfer = false }
        if (buyPlans) com.simple.mobile.presentation.plans.PlanShopScreen(state, model,
            onClose = { buyPlans = false }, onPermissions = onConnect, onRefresh = onRefreshAll)
        if (rechargeOpen) com.simple.mobile.presentation.recharge.RechargeScreen(state, onClose = { rechargeOpen = false }, onPermissions = onConnect) { subscription ->
            if (com.simple.mobile.BuildConfig.DIRECT_DISTRIBUTION) com.simple.mobile.presentation.recharge.BankRechargeNotices(state.copy(selected = subscription),
                onEnabled = { enabled -> model.setBankSms(enabled); if (enabled) onSmsEnabled(true) },
                onSync = onRescanSms)
        }
        rename?.let { line ->
            AlertDialog(onDismissRequest = { rename = null }, title = { Text("Datos de la línea") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { OutlinedTextField(value = nickname, onValueChange = { nickname = it.filterNot { c -> c.isISOControl() }.take(40) },
                    label = { Text("Ej. Personal o Trabajo") }, supportingText = { Text("${nickname.length}/40 · Vacío restaura el nombre original") }, singleLine = true)
                    OutlinedTextField(value = linePhone, onValueChange = { linePhone = it.filter { c -> c in '0'..'9' }.take(8) },
                        label = { Text("Número de esta línea") }, prefix = { Text("+53 ") },
                        supportingText = { Text("Se usará al recargar esta línea. Comprueba que sea correcto.") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone), singleLine = true)
                } },
                confirmButton = { TextButton(enabled = linePhone.isEmpty() || Regex("[56][0-9]{7}").matches(linePhone), onClick = { model.renameLine(line.subscription, nickname, linePhone); rename = null }) { Text("Guardar") } },
                dismissButton = { TextButton(onClick = { rename = null }) { Text("Cancelar") } })
        }
        if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("¿Eliminar datos de $deleteLabel?") },
            text = { Text("Se borrarán las lecturas, nombres, avisos de recarga y registros de transferencia de ${if (deleteSubscription == null) "todas las líneas" else "esta línea"}. ${if (deleteSubscription == null) "También se borrarán todos los destinatarios guardados. " else "Los destinatarios guardados se conservarán. "}Se desactivará la importación SMS. No se puede deshacer.") },
            confirmButton = { TextButton(onClick = { delete = false; model.clearHistory(deleteSubscription) }, enabled = !state.busy && !state.managingLine && !state.transferSaving) { Text("Eliminar") } },
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
            Box(Modifier.size(38.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.EventAvailable, null, tint = Teal, modifier = Modifier.size(23.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("RECARGA NACIONAL", fontSize = 12.sp, letterSpacing = 1.sp, color = Muted)

            }
        }
        Text(title, fontSize = 18.sp, lineHeight = 24.sp, color = Teal, fontWeight = FontWeight.Bold)
        Text(when {
            datePassed -> "Actualiza para confirmar cuánto puedes recargar hoy. La fecha por sí sola no confirma el cupo."
            next != null -> "El operador indica cuándo vuelve a estar disponible. Esta fecha no es el vencimiento de tu línea."
            reading == null -> "Consulta el límite y la fecha de reapertura de tu recarga en CUP."
            else -> "Según la última respuesta del operador. Consulta de nuevo antes de recargar."
        }, fontSize = 12.sp, color = Muted)

        reading?.receivedAt()?.let { Text("${freshnessLabel(it, now)} · ${stamp(it)}", color = Muted, fontSize = 12.sp) }
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
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
@Composable private fun SectionTitle(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.semantics { heading() })
        Text(detail, fontSize = 13.sp, lineHeight = 19.sp, color = Muted)
    }
}
@Composable private fun QuickAction(title: String, icon: ImageVector, modifier: Modifier, action: () -> Unit) {
    Surface(onClick = action, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), modifier = modifier) {
        Column(Modifier.heightIn(min = 88.dp).padding(horizontal = 6.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterVertically)) {
            Icon(icon, null, tint = Teal, modifier = Modifier.size(24.dp))
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
@Composable private fun SimResource(title: String, value: String, icon: ImageVector,
    enabled: Boolean, reading: CarrierReading?, now: Long, querying: Boolean,
    expiry: Long? = reading?.expiresAt(), modifier: Modifier = Modifier, note: String? = null, action: () -> Unit) {
    val expired = isExpired(expiry, now)
    val secondary = Color(0xFFDDCEED)
    val detail = when {
        querying -> "Consultando…"
        reading == null -> "Sin lectura"
        expiry != null -> expirationLabel(expiry, now)
        else -> freshnessLabel(reading.receivedAt(), now)
    }
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.07f))
        .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Consultar $title", onClick = action)
        .semantics(mergeDescendants = true) {
            contentDescription = "$title: $value. " + (note?.let { "$it. " } ?: "") + detail
        }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (querying) CircularProgressIndicator(Modifier.size(16.dp), color = Mint, strokeWidth = 2.dp)
            else Icon(icon, null, tint = Mint, modifier = Modifier.size(16.dp))
            Text(title, color = secondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
        }
        Text(value, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 20.sp,
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
        note?.let { Text(it, color = secondary, fontSize = 11.sp, lineHeight = 14.sp) }
        Spacer(Modifier.weight(1f))
        Text(detail, color = if (expired && !querying) Color(0xFFFFC4BA) else secondary,
            fontSize = 10.sp, lineHeight = 13.sp)
    }
}
@Composable private fun SettingsRow(label: String, icon: ImageVector, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = action).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Teal, modifier = Modifier.size(21.dp)); Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f), fontSize = 15.sp); Icon(Icons.Outlined.ChevronRight, null, tint = Muted)
    }
}
@Composable private fun PrivacyFooter() {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Lock, null, tint = Muted, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(6.dp))
        Text("Guardado automático · Cifrado en tu dispositivo", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f, fill = false))
    }
}
private fun stamp(at: Long) = SimpleDateFormat("d MMM · HH:mm", Locale("es")).format(Date(at))
private fun expiryLabel(at: Long) = SimpleDateFormat("d MMM yyyy", Locale("es")).apply { timeZone = java.util.TimeZone.getTimeZone("America/Havana") }.format(Date(at - 1))
private fun number(value: BigDecimal) = value.stripTrailingZeros().toPlainString()
private fun gb(bytes: Long?) = bytes?.let { number(BigDecimal(it).divide(BigDecimal(1_000_000_000), 3, java.math.RoundingMode.DOWN)) } ?: "—"
private fun bundleLabel(kind: String) = when(kind) { "general" -> "Internet · todas las redes"; "national" -> "Navegación nacional"; "lte" -> "Solo LTE / 4G"; "night" -> "Navegación nocturna"; "mail" -> "Correo nauta"; "todus" -> "toDus"; "whatsapp" -> "WhatsApp"; "facebook" -> "Facebook"; else -> "Datos" }
private fun queryLabel(query: CubacelQuery) = when(query) { CubacelQuery.BALANCE -> "Saldo principal"; CubacelQuery.DATA -> "Datos móviles"; CubacelQuery.VOICE -> "Minutos"; CubacelQuery.SMS -> "Mensajes"; CubacelQuery.BONUSES_AND_USD_PLANS -> "Bonos y planes USD"; CubacelQuery.RECHARGE_STATUS -> "Recargas nacionales" }
private fun historyDate(at: Long) = SimpleDateFormat("d 'de' MMMM yyyy", Locale.forLanguageTag("es")).format(Date(at))
private fun readingTitle(reading: CarrierReading) = when {
    reading.purchase() != null -> "Plan comprado"
    reading.receivedRechargeCup() != null -> "Recarga recibida"
    else -> queryLabel(reading.query())
}
private fun readingIcon(query: CubacelQuery) = when (query) {
    CubacelQuery.BALANCE -> Icons.Outlined.AccountBalanceWallet
    CubacelQuery.DATA -> Icons.Outlined.DataUsage
    CubacelQuery.VOICE -> Icons.Outlined.Call
    CubacelQuery.SMS -> Icons.Outlined.Sms
    CubacelQuery.BONUSES_AND_USD_PLANS -> Icons.Outlined.Redeem
    CubacelQuery.RECHARGE_STATUS -> Icons.Outlined.AddCard
}
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

@Composable private fun SmsConnectionCard(state: DashboardState, onEnabled: (Boolean) -> Unit,
                                         onSync: () -> Unit, onPermissions: () -> Unit, onRescan: () -> Unit) {
    val status = state.smsStatus
    val permissionsReady = state.permission && state.smsReadAllowed && state.smsReceiveAllowed
    WhiteCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Mensajes de Cubacel", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(when {
                    !status.enabled -> "Importación desactivada"
                    !permissionsReady -> "Conexión incompleta · faltan permisos"
                    status.syncing -> "Sincronizando…"
                    else -> "Importación automática activada"
                }, fontSize = 12.sp, color = Muted, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            Switch(checked = status.enabled, onCheckedChange = onEnabled,
                enabled = !state.managingLine && (!status.syncing || status.enabled),
                modifier = Modifier.semantics { contentDescription = "Importación automática de SMS Cubacel" })
        }
        Text("Solo mensajes reconocidos del operador, de todas tus SIM Cubacel activas.", color = Muted, fontSize = 12.sp)
        status.lastAt?.let {
            Text("Última sincronización · ${stamp(it)}", fontSize = 12.sp)
            Text("${status.lastAdded} registros nuevos añadidos. Los duplicados no se cuentan.", color = Muted, fontSize = 12.sp)
        } ?: Text("Todavía no se ha completado una sincronización.", color = Muted, fontSize = 12.sp)
        status.error?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
        if (status.syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (status.enabled && !permissionsReady) {
            Text("Se necesita acceso al teléfono, lectura de SMS y recepción de mensajes para la conexión completa.", fontSize = 12.sp, color = Muted)
            TextButton(onClick = onPermissions) { Text("Revisar permisos en Android") }
        }
        if (status.moreInbox) Text("Quedan mensajes nuevos por revisar. Sincroniza de nuevo para continuar.", fontSize = 13.sp)
        DetailDisclosure("Detalles de importación") {
            Text("La primera revisión incluye hasta 150 SMS del operador y, si lo activas en Recargar, de PAGOxMOVIL. Después se revisan solo los nuevos.", color = Muted, fontSize = 12.sp)
            TextButton(onClick = onRescan, enabled = status.enabled && permissionsReady && !status.syncing && !state.managingLine) { Text("Revisar últimos 150 mensajes") }
        }
        OutlinedButton(onClick = onSync,
            enabled = status.enabled && state.permission && state.smsReadAllowed && !status.syncing && !state.managingLine,
            modifier = Modifier.fillMaxWidth()) { Text(if (status.syncing) "Sincronizando…" else "Sincronizar ahora") }
    }
}

@Composable private fun AttentionCard(notices: List<DashboardInsights.Notice>, enabled: Boolean, now: Long,
    query: (CubacelQuery) -> Unit, refresh: () -> Unit, recharge: () -> Unit, plans: () -> Unit) {
    var expanded by remember(notices) { mutableStateOf(false) }
    if (notices.isEmpty()) return
    WhiteCard {
        (if (expanded) notices else notices.take(1)).forEach { notice ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Info, null, tint = Teal, modifier = Modifier.size(20.dp))
                    Text(notice.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f).semantics { heading() })
                }
                Text(notice.detail, fontSize = 13.sp, color = Muted)
                notice.at?.let { Text(freshnessLabel(it, now), fontSize = 12.sp, color = Muted) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { if (notice.refreshAll) refresh() else query(notice.query) }, enabled = enabled) {
                        Text(if (notice.refreshAll) "Actualizar todo" else "Consultar")
                    }
                    if (!notice.refreshAll) {
                        TextButton(onClick = if (notice.query == CubacelQuery.BALANCE) recharge else plans) {
                            Text(if (notice.query == CubacelQuery.BALANCE) "Recargar" else "Comprar planes")
                        }
                    }
                }
            }
        }
        if (notices.size > 1) TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Ver menos" else "Ver ${notices.size - 1} avisos más")
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
        }
    }
}

@Composable private fun UsageTrendsCard(trends: List<DashboardInsights.Trend>, enabled: Boolean,
    refresh: () -> Unit, history: () -> Unit) {
    WhiteCard {
        SectionTitle("Cómo cambian tus recursos", "Últimas dos lecturas de cada recurso · últimos 7 días")
        if (trends.isEmpty()) {
            Text("Necesitas dos lecturas del mismo recurso para compararlo. Consulta hoy y vuelve después de usar tu línea.", color = Muted, fontSize = 14.sp)
            OutlinedButton(onClick = refresh, enabled = enabled) { Text("Actualizar recursos") }
        } else {
            trends.forEachIndexed { index, trend ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val unit = when (trend.query) {
                    CubacelQuery.BALANCE -> "CUP"; CubacelQuery.DATA -> "GB"; CubacelQuery.VOICE -> "min"; else -> "SMS"
                }
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(if (trend.query == CubacelQuery.DATA) "Internet general" else queryLabel(trend.query), fontWeight = FontWeight.SemiBold)
                    Text("${number(trend.before)} → ${number(trend.after)} $unit", fontSize = 20.sp, color = Teal, fontWeight = FontWeight.Bold)
                    Text("${stamp(trend.from)} → ${stamp(trend.to)}", fontSize = 12.sp, color = Muted)
                    Text(trend.explanation ?: if (trend.reduction?.signum() == 0) "Sin cambios entre lecturas"
                        else "Reducción observada: ${number(trend.reduction!!)} $unit", fontSize = 13.sp, color = Muted)
                }
            }
            Text("Son cambios entre consultas, no una medición continua del consumo. Compras, recargas y vencimientos pueden modificar los valores.", fontSize = 12.sp, color = Muted)
        }
        TextButton(onClick = history) { Text("Ver historial completo"); Spacer(Modifier.width(6.dp)); Icon(Icons.Outlined.ArrowForward, null, modifier = Modifier.size(18.dp)) }
    }
}
