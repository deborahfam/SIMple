package com.simple.mobile.presentation.plans

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simple.mobile.domain.plans.*
import com.simple.mobile.presentation.DashboardState
import com.simple.mobile.presentation.DashboardViewModel
import com.simple.mobile.presentation.DetailDisclosure

@Composable fun PlanPurchaseScreen(state: DashboardState, model: DashboardViewModel, onClose: () -> Unit,
    onPermissions: () -> Unit, onRefresh: () -> Unit) {
    val purchase by model.purchases.state.collectAsStateWithLifecycle()
    val initialSubscription by rememberSaveable { mutableIntStateOf(state.selected) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var reviewing by rememberSaveable { mutableStateOf(false) }
    var acknowledged by rememberSaveable(purchase.attempt?.id) { mutableStateOf(false) }
    val attempt = purchase.attempt
    val subscription = attempt?.subscription ?: initialSubscription
    val plan = PlanCatalog.plans.firstOrNull { it.id == (attempt?.planId ?: selectedId) }
    val line = state.displayLines.firstOrNull { it.subscription == subscription }
    val eligible = state.lines.any { it.subscription == subscription && it.active && it.cubacel }
    val context = LocalContext.current
    val permitted = context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED && state.permission
    val available = eligible && permitted && state.storageReady && !state.busy && !state.managingLine && !state.transferSaving
    val scroll = rememberScrollState()
    val step = if (attempt != null) 2 else if (reviewing) 1 else 0
    LaunchedEffect(step) { scroll.scrollTo(0) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false, securePolicy = if (attempt != null) SecureFlagPolicy.SecureOn else SecureFlagPolicy.Inherit)) {
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
                    Text("Comprar planes", modifier = Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Cerrar planes") }
                }
            }, bottomBar = {
                Surface {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val button = Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(min = 48.dp)
                        when {
                            attempt != null -> OutlinedButton(onClick = onClose, modifier = button) { Text("Volver a mi línea") }
                            !reviewing -> Button(onClick = { reviewing = true }, enabled = plan != null && purchase.ready && !purchase.busy,
                                modifier = button) { Text("Revisar plan${plan?.let { " · ${it.priceCup} CUP" } ?: ""}") }
                            !permitted -> Button(onClick = onPermissions, modifier = button) { Text("Permitir consultas y compras") }
                            else -> Button(onClick = { plan?.let { model.purchasePlan(it.id, subscription) } },
                                enabled = available && plan != null && purchase.ready && !purchase.busy, modifier = button) {
                                Text("Confirmar compra · ${plan?.priceCup ?: "—"} CUP")
                            }
                        }
                    }
                }
            }) { padding ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(scroll).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Row(Modifier.fillMaxWidth().semantics { contentDescription = "Paso ${step + 1} de 3" }, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf("Elegir", "Revisar", "Resultado").forEachIndexed { index, text ->
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    HorizontalDivider(thickness = if (index == step) 3.dp else 1.dp,
                                        color = if (index == step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                                    Text(text, style = MaterialTheme.typography.labelLarge, color = if (index == step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Outlined.SimCard, null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text(line?.label ?: "Línea $subscription", style = MaterialTheme.typography.titleSmall)
                                Text("Pago con saldo principal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        purchase.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (!purchase.ready && purchase.error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (!eligible && attempt == null) Text("Selecciona una SIM Cubacel activa en Inicio para comprar.", color = MaterialTheme.colorScheme.error)
                        if (state.busy && attempt == null) Text("Espera a que termine la consulta en curso.")
                        if (!state.storageReady && attempt == null) Text("Reintenta el almacenamiento desde Ajustes antes de comprar.", color = MaterialTheme.colorScheme.error)
                        if (attempt != null) {
                            if (plan != null) PlanSummary(plan)
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
                                TextButton(onClick = { model.purchases.acknowledge(); reviewing = false; selectedId = null }, enabled = acknowledged) { Text("Elegir otro plan") }
                            }
                        } else if (!reviewing) {
                            Text("Internet a tu medida", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            Text("Cuatro opciones para navegar. Los combinados incluyen minutos y mensajes nacionales.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                PlanCatalog.plans.forEach { offer ->
                                    val selected = offer.id == selectedId
                                    Surface(shape = MaterialTheme.shapes.large,
                                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                                        Column(Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton,
                                            onClick = { selectedId = offer.id }).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Column(Modifier.weight(1f)) {
                                                    Text(if (offer.minutes == 0) "SOLO INTERNET" else "COMBINADO", style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text(offer.data, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                                                }
                                                RadioButton(selected = selected, onClick = null)
                                            }
                                            Text(offer.resources, style = MaterialTheme.typography.bodyLarge)
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                            Text("${offer.priceCup} CUP", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                            Text("+ 300 MB nacionales · 35 días desde el primer uso", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                            Text("Ofertas de ETECSA · revisadas el ${PlanCatalog.VERIFIED_ON}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if (plan != null) {
                            PlanSummary(plan)
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
}

@Composable private fun PlanSummary(plan: MobilePlan) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(plan.title, style = MaterialTheme.typography.titleLarge)
            Text("${plan.priceCup} CUP", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            HorizontalDivider()
            Text(plan.resources)
            Text("+ 300 MB para navegación nacional")
            Text("35 días desde el primer uso", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
