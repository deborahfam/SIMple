package com.simple.mobile.presentation.plans

import androidx.activity.compose.BackHandler
import com.simple.mobile.presentation.recharge.RechargeLineSelector
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simple.mobile.domain.plans.*
import com.simple.mobile.presentation.DashboardState
import com.simple.mobile.presentation.DashboardViewModel
import com.simple.mobile.presentation.FlowProgress
import com.simple.mobile.presentation.PurchaseReviewCard
import com.simple.mobile.presentation.DetailDisclosure

@Composable internal fun PlanPurchaseContent(state: DashboardState, model: DashboardViewModel, onClose: () -> Unit,
    onPermissions: () -> Unit, onRefresh: () -> Unit, onCategories: (() -> Unit)? = null) {
    val purchase by model.purchases.state.collectAsStateWithLifecycle()
    val bankRecharge by model.bankRecharges.state.collectAsStateWithLifecycle()
    var selectedSubscription by rememberSaveable { mutableIntStateOf(state.selected) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewing by rememberSaveable { mutableStateOf(false) }
    var acknowledged by rememberSaveable(purchase.attempt?.id) { mutableStateOf(false) }
    val attempt = purchase.attempt
    val back: () -> Unit = {
        when {
            attempt != null -> onClose()
            reviewing -> reviewing = false
            onCategories != null -> onCategories()
            else -> onClose()
        }
    }
    BackHandler(onBack = back)
    val subscription = attempt?.subscription ?: selectedSubscription
    val plan = PlanCatalog.plans.firstOrNull { it.id == (attempt?.planId ?: selectedId) }
    val line = state.displayLines.firstOrNull { it.subscription == subscription }
    val eligible = state.lines.any { it.subscription == subscription && it.active && it.cubacel }
    val context = LocalContext.current
    val permitted = context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED && state.permission
    val available = eligible && permitted && state.storageReady && !state.busy && !state.managingLine && !state.transferSaving && !bankRecharge.busy
    val scroll = rememberScrollState()
    val step = if (attempt != null) 2 else if (reviewing) 1 else 0
    LaunchedEffect(step) { scroll.scrollTo(0) }
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    val dark = isSystemInDarkTheme()
    SideEffect {
        window?.let { WindowCompat.getInsetsController(it, it.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        } }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(Modifier.fillMaxSize().safeDrawingPadding(), contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (reviewing && attempt == null) {
                    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver a elegir plan") }
                } else if (attempt == null && onCategories != null) {
                    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver a categorías") }
                }
                Text(if (attempt != null) "Tu solicitud" else if (reviewing) "Revisar compra" else "Comprar planes", modifier = Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Cerrar planes") }
            }
        }, bottomBar = {
            Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (attempt == null) {
                        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                            Text(if (plan == null) "Elige un plan para continuar" else if (reviewing) "Total a pagar · ${plan.priceCup} CUP" else "${plan.title} · ${plan.priceCup} CUP",
                                style = MaterialTheme.typography.titleSmall)
                            Text(if (reviewing) "Con el saldo principal de tu línea" else "Podrás revisar los detalles antes de comprar",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val button = Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(min = 52.dp)
                    when {
                        attempt != null -> OutlinedButton(onClick = onClose, modifier = button) { Text("Volver a mi línea") }
                        !reviewing -> Button(onClick = { reviewing = true }, enabled = plan != null && purchase.ready && !purchase.busy,
                            modifier = button) { Text("Continuar"); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, modifier = Modifier.size(18.dp)) }
                        !permitted -> Button(onClick = onPermissions, modifier = button) { Text("Permitir consultas y compras") }
                        else -> Button(onClick = { plan?.let { model.purchasePlan(it.id, subscription) } },
                            enabled = available && plan != null && purchase.ready && !purchase.busy, modifier = button) {
                            Text("Confirmar compra")
                        }
                    }
                }
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FlowProgress(step, listOf("Elige tu plan", "Revisa tu compra", "Tu resultado"))
                    if (attempt == null && !reviewing) {
                        RechargeLineSelector(state, selectedSubscription, source = true, title = "SIM para comprar datos") {
                            selectedSubscription = it
                        }
                    }
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Outlined.SimCard, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Column(Modifier.weight(1f)) {
                                Text(line?.label ?: "Línea $subscription", style = MaterialTheme.typography.titleSmall)
                                Text("Pago con saldo principal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    purchase.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (!purchase.ready && purchase.error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (!eligible && attempt == null) Text("Selecciona una SIM Cubacel activa para comprar.", color = MaterialTheme.colorScheme.error)
                    if (bankRecharge.busy && attempt == null) Text("Espera a que termine la operación bancaria en curso.")
                    if (state.busy && attempt == null) Text("Espera a que termine la consulta en curso.")
                    if (!state.storageReady && attempt == null) Text("Reintenta el almacenamiento desde Ajustes antes de comprar.", color = MaterialTheme.colorScheme.error)
                    if (attempt != null) {
                        if (plan != null) PlanSummary(plan, line?.label ?: "SIM $subscription", state.linePhones[subscription])
                        val title = when (attempt.phase) {
                            PurchasePhase.SENDING -> "Enviando solicitud a Cubacel"
                            PurchasePhase.RESPONSE -> "Respuesta de Cubacel"
                            PurchasePhase.UNKNOWN -> "Compra sin confirmar"
                            PurchasePhase.NOT_SENT -> "La solicitud no se envió"
                        }
                        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        if (purchase.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(when (attempt.phase) {
                            PurchasePhase.SENDING -> "Se envía una sola solicitud. Cerrar esta pantalla no cancela una operación que ya recibió el operador."
                            PurchasePhase.RESPONSE -> "Lee la respuesta antes de iniciar otra compra. Recibir una respuesta no garantiza que el plan se haya activado."
                            PurchasePhase.UNKNOWN -> "No hay confirmación del resultado. El operador podría haber procesado la compra. Revisa tus recursos y SMS antes de volver a comprar."
                            PurchasePhase.NOT_SENT -> "Comprueba los permisos, la SIM y si hay otra sesión USSD abierta. Para volver a intentarlo, inicia una nueva revisión."
                        })
                        purchase.response?.let { reply ->
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                                Text(reply, modifier = Modifier.fillMaxWidth().padding(16.dp))
                            }
                        }
                        if (!purchase.busy) {
                            OutlinedButton(onClick = { model.select(subscription); onClose(); onRefresh() }, enabled = eligible && !state.busy && !state.managingLine && state.storageReady,
                                modifier = Modifier.fillMaxWidth()) { Text("Consultar saldo y recursos") }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
                                Text("He revisado el resultado antes de iniciar otra compra.", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { selectedSubscription = subscription; model.purchases.acknowledge(); reviewing = false; selectedId = null }, enabled = acknowledged) { Text("Elegir otro plan") }
                        }
                    } else if (!reviewing) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Conéctate a tu manera", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("Elige datos o un combinado con minutos y SMS.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            PlanCatalog.plans.forEach { offer ->
                                PlanOption(offer, selected = offer.id == selectedId, onSelect = { selectedId = offer.id })
                            }
                        }
                        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Incluido en todos los planes", style = MaterialTheme.typography.labelLarge)
                                    Text("300 MB de navegación nacional y 35 días de vigencia desde el primer uso.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Text("Ofertas de ETECSA · revisadas el ${PlanCatalog.VERIFIED_ON}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (plan != null) {
                        PlanSummary(plan, line?.label ?: "SIM $subscription", state.linePhones[subscription])
                        Text("Se descontarán ${plan.priceCup} CUP del saldo de ${line?.label ?: "la SIM seleccionada"} al completar la compra.", style = MaterialTheme.typography.bodyLarge)
                        Text("La compra directa está pendiente de validación con Cubacel. Si no llega confirmación, revisa tus recursos antes de volver a comprar.", style = MaterialTheme.typography.bodySmall)
                        DetailDisclosure("Código y condiciones") {
                            Text("Código a enviar: ${plan.code}")
                            Text("Una solicitud, sin reintentos automáticos. Los precios son los publicados, no una cotización en tiempo real. La vigencia empieza con el primer uso, no al seleccionar el plan.")
                        }
                        TextButton(onClick = { reviewing = false }) { Text("Cambiar plan") }
                    }
                }
            }
        }
    }
}

@Composable private fun PlanOption(plan: MobilePlan, selected: Boolean, onSelect: () -> Unit) {
    val largeText = LocalConfiguration.current.fontScale > 1.2f
    Surface(shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (plan.minutes == 0) "SOLO INTERNET" else "DATOS + MINUTOS + SMS",
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(22.dp))
            }
            if (largeText) {
                PlanData(plan)
                PlanPrice(plan)
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    PlanData(plan)
                    PlanPrice(plan)
                }
            }
            Text(if (plan.minutes == 0) "Para navegar y usar tus apps" else "${plan.minutes} min nacionales  ·  ${plan.sms} SMS",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun PlanData(plan: MobilePlan) {
    Text(plan.data, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
}

@Composable private fun PlanPrice(plan: MobilePlan) {
    Text("${plan.priceCup} CUP", style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
}

@Composable private fun PlanSummary(plan: MobilePlan, line: String, phone: String?) {
    PurchaseReviewCard(title = if (plan.minutes == 0) "Tu plan de Internet" else "Tu plan combinado",
        line = line, destination = phone?.takeIf { it.isNotBlank() }?.let { "$line · +53 $it" } ?: line,
        amount = "${plan.priceCup} CUP", paymentMethod = "Saldo principal de la línea") {
        Text(plan.data, style = MaterialTheme.typography.headlineSmall)
        if (plan.minutes > 0) Text("${plan.minutes} minutos nacionales · ${plan.sms} SMS")
        Text("+ 300 MB de navegación nacional", style = MaterialTheme.typography.bodyMedium)
        Text("35 días desde el primer uso", style = MaterialTheme.typography.bodyMedium)
    }
}
