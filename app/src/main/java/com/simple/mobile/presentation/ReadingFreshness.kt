package com.simple.mobile.presentation

internal fun isExpired(expiresAt: Long?, now: Long): Boolean = expiresAt != null && now >= expiresAt

internal fun freshnessLabel(receivedAt: Long, now: Long): String {
    val minutes = ((now - receivedAt).coerceAtLeast(0) / 60_000)
    return when {
        minutes < 1 -> "Actualizado ahora"
        minutes < 60 -> "Actualizado hace $minutes min"
        minutes < 1_440 -> "Actualizado hace ${minutes / 60} h"
        minutes < 2_880 -> "Actualizado hace 1 día"
        else -> "Actualizado hace ${minutes / 1_440} días"
    }
}
