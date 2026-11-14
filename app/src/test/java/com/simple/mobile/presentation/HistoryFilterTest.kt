package com.simple.mobile.presentation

import com.simple.mobile.data.local.EncryptedReadingStore.Entry
import com.simple.mobile.data.parser.CubacelParser
import com.simple.mobile.domain.carrier.CubacelQuery
import org.junit.Assert.*
import org.junit.Test

class HistoryFilterTest {
    @Test fun combinesCategoryAndTimeWindowAndKeepsBoundary() {
        val now = 200_000_000L
        val parser = CubacelParser()
        val rows = listOf(Entry(1, parser.parse(CubacelQuery.BALANCE, "Saldo: 10 CUP", now - 86_400_000)),
            Entry(1, parser.parse(CubacelQuery.BALANCE, "Saldo: 20 CUP", now - 86_400_001)),
            Entry(1, parser.parse(CubacelQuery.SMS, "Dispone de 5 SMS", now)))
        assertEquals(1, HistoryFilter.apply(rows, "BALANCE", 1, now).size)
        assertEquals(2, HistoryFilter.apply(rows, null, 1, now).size)
        assertEquals(3, HistoryFilter.apply(rows, null, 0, now).size)
        assertEquals(CubacelQuery.SMS, HistoryFilter.apply(rows, null, 0, now).first().reading.query())
    }
}
