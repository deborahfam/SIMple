package com.simple.mobile.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

@Composable internal fun FlowProgress(step: Int, labels: List<String>) {
    val label = labels[step]
    Row(Modifier.fillMaxWidth().clearAndSetSemantics {
        contentDescription = "Paso ${step + 1} de ${labels.size}. $label"
        liveRegion = LiveRegionMode.Polite
    }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Paso ${step + 1} de ${labels.size}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(labels.size) { index ->
                Box(Modifier.size(width = if (index == step) 24.dp else 6.dp, height = 6.dp)
                    .background(if (index <= step) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant, CircleShape))
            }
        }
    }
}

