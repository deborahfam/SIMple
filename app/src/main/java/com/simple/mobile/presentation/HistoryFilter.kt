package com.simple.mobile.presentation

import com.simple.mobile.data.local.EncryptedReadingStore.Entry

internal object HistoryFilter {
    fun apply(entries: List<Entry>, category: String?, days: Int, now: Long): List<Entry> =
        entries.filter { (category == null || it.reading.query().name == category) &&
            (days == 0 || it.reading.receivedAt() in (now - days * 86_400_000L)..now) }
            .sortedByDescending { it.reading.receivedAt() }
}
