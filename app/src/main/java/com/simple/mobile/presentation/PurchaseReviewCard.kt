package com.simple.mobile.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Shared layout; callers supply the actual payment source, never infer it from the destination. */
@Composable internal fun PurchaseReviewCard(title: String, line: String, destination: String,
    amount: String, paymentMethod: String, details: @Composable ColumnScope.() -> Unit = {}) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(amount, style = MaterialTheme.typography.headlineSmall)
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
            ReviewField("Línea de pago", line)
            ReviewField("Destino", destination)
            ReviewField("Forma de pago", paymentMethod)
            details()
        }
    }
}

@Composable private fun ReviewField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
