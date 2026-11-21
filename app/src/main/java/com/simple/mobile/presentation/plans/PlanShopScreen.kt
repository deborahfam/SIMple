package com.simple.mobile.presentation.plans

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simple.mobile.domain.plans.*
import com.simple.mobile.domain.recharge.MetropolitanoProtocol
import com.simple.mobile.presentation.DashboardState
import com.simple.mobile.presentation.DashboardViewModel
import com.simple.mobile.presentation.FlowProgress
import com.simple.mobile.presentation.recharge.RechargeLineSelector

@Composable fun PlanShopScreen(state: DashboardState, model: DashboardViewModel, onClose: () -> Unit,
    onPermissions: () -> Unit, onRefresh: () -> Unit) {
    var route by rememberSaveable { mutableStateOf<String?>(null) }
    val purchase by model.purchases.state.collectAsStateWithLifecycle()
    // Keep one window alive when switching categories, including restored purchases.
    val showingData = route == "DATA" || purchase.attempt != null
    LaunchedEffect(purchase.attempt?.id) {
        if (purchase.attempt != null) route = "DATA"
    }
    val category = BankPlanCategory.entries.firstOrNull { it.name == route }
    var destination by rememberSaveable { mutableIntStateOf(state.selected) }
    var option by rememberSaveable(route) { mutableStateOf<Int?>(null) }
    var reviewing by rememberSaveable(route) { mutableStateOf(false) }
    var opened by rememberSaveable(route) { mutableStateOf(false) }
    var error by rememberSaveable(route) { mutableStateOf<String?>(null) }
    val phone = state.linePhones[destination].orEmpty()
    val offer = category?.let { BankPlanCatalog.offers(it).firstOrNull { offer -> offer.option == option } }
    val blockedReason = when {
        !state.storageReady -> "Reintenta el almacenamiento desde Ajustes antes de continuar."
        state.busy -> "Espera a que termine la consulta en curso."
        state.managingLine -> "Espera a que termine de guardarse la línea."
        purchase.busy -> "Espera a que termine la compra en curso."
        phone.isBlank() -> "Añade el número de esta línea en Ajustes → Editar línea."
        MetropolitanoProtocol.phone(phone) == null -> "Corrige el número en Ajustes → Editar línea: debe ser un móvil cubano válido."
        offer == null -> "Selecciona un plan para continuar."
        else -> null
    }
    val context = LocalContext.current
    val scroll = rememberScrollState()
    LaunchedEffect(route, reviewing, opened) { scroll.scrollTo(0) }
    val back: () -> Unit = {
        when { opened -> { opened = false; reviewing = false }; reviewing -> reviewing = false; route != null -> route = null; else -> onClose() }
    }
    Dialog(onDismissRequest = { if (showingData) onClose() else back() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
            securePolicy = if (purchase.attempt != null) SecureFlagPolicy.SecureOn else SecureFlagPolicy.Inherit)) {
        if (showingData) {
            PlanPurchaseContent(state, model, onClose = onClose, onPermissions = onPermissions,
                onRefresh = onRefresh, onCategories = { route = null })
        } else {
            BackHandler(onBack = back)
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Scaffold(Modifier.fillMaxSize().safeDrawingPadding(), contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (category != null) IconButton(onClick = back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") }
                        Text(category?.let { "Planes de ${it.title}" } ?: "Comprar planes",
                            modifier = Modifier.weight(1f).padding(start = 12.dp).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Cerrar planes") }
                    }
                }, bottomBar = {
                    if (category != null) Surface(shadowElevation = 8.dp) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!opened && blockedReason != null) Text(blockedReason,
                                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = {
                                error = null
                                when {
                                    opened -> onClose()
                                    !reviewing -> reviewing = true
                                    else -> try {
                                        // ACTION_DIAL leaves SIM selection and every priced choice to the user.
                                        val code = MetropolitanoProtocol.planMenu(phone, category)
                                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", code, null)))
                                        opened = true
                                    } catch (_: ActivityNotFoundException) {
                                        error = "No hay una aplicación de teléfono disponible."
                                    } catch (_: SecurityException) {
                                        error = "Android no permitió abrir el menú."
                                    } catch (_: IllegalArgumentException) {
                                        error = "Comprueba el número de la línea en Ajustes."
                                    }
                                }
                            }, enabled = opened || blockedReason == null,
                                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(min = 52.dp)) {
                                Text(when { opened -> "Volver a mi línea"; reviewing -> "Abrir menú del banco"; else -> "Continuar" })
                            }
                        }
                    }
                }) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                        Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(scroll).padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (category == null) {
                                Text("¿Qué necesitas?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                listOf(Triple("DATA", "Datos", "Internet y combinados"),
                                    Triple("VOICE", "Voz", "Minutos nacionales"), Triple("SMS", "SMS", "Mensajes nacionales")).forEach { (id, title, subtitle) ->
                                    Surface(onClick = { route = id }, shape = MaterialTheme.shapes.extraLarge,
                                        color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                                        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                            Icon(when (id) { "DATA" -> Icons.Outlined.Language; "VOICE" -> Icons.Outlined.Call; else -> Icons.Outlined.Sms },
                                                null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null)
                                        }
                                    }
                                }
                            } else {
                                FlowProgress(if (opened) 2 else if (reviewing) 1 else 0, listOf("Elige tu plan", "Revisa", "En el banco"))
                                Text("Metropolitano · Cuenta CUP", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                if (!reviewing && !opened) {
                                    RechargeLineSelector(state, destination) { destination = it }
                                    Text(if (category == BankPlanCategory.VOICE) "Minutos para hablar" else "Mensajes para compartir",
                                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        BankPlanCatalog.offers(category).forEach { item ->
                                            val selected = option == item.option
                                            Surface(shape = MaterialTheme.shapes.large,
                                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                                                Row(Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = { option = item.option })
                                                    .padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Text("${item.quantity} ${category.unit}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                                        Text(item.priceLabel, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                                                    }
                                                    Icon(if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null)
                                                }
                                            }
                                        }
                                    }
                                    Text("Pago desde tu cuenta bancaria.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else if (offer != null) {
                                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraLarge) {
                                        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text("${offer.quantity} ${category.unit}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                                            Text(offer.priceLabel, style = MaterialTheme.typography.headlineSmall)
                                            HorizontalDivider()
                                            Text(state.displayLines.firstOrNull { it.subscription == destination }?.label ?: "Línea de destino")
                                            Text("+53 $phone", style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                    Text(if (opened) "Continúa en el menú del banco" else "Revisa tu plan", style = MaterialTheme.typography.titleLarge)
                                    Text("En Teléfono, usa la SIM vinculada a Metropolitano y marca. En el menú de ${category.title}, elige la opción ${offer.option}: ${offer.quantity} ${category.unit}.",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Comprueba el precio que indique el banco antes de confirmar. Abrir el menú no compra el plan.",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (opened) Text("SIMple no puede confirmar el resultado de esta operación. Revisa el mensaje del banco.",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
