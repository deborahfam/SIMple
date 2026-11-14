package com.simple.mobile.presentation

import org.junit.Assert.*
import org.junit.Test

class ReadingFreshnessTest {
    @Test fun showsAgeAcrossMinuteHourAndDayBoundaries() {
        assertEquals("Actualizado ahora", freshnessLabel(0, 59_999))
        assertEquals("Actualizado hace 1 min", freshnessLabel(0, 60_000))
        assertEquals("Actualizado hace 1 h", freshnessLabel(0, 3_600_000))
        assertEquals("Actualizado hace 1 día", freshnessLabel(0, 86_400_000))
    }
    @Test fun futureTimestampDoesNotShowNegativeAge() {
        assertEquals("Actualizado ahora", freshnessLabel(120_000, 60_000))
    }
    @Test fun expirationUsesExactExclusiveBoundaryAndKeepsUnknownUnknown() {
        assertFalse(isExpired(null, 100))
        assertFalse(isExpired(100, 99))
        assertTrue(isExpired(100, 100))
        assertTrue(isExpired(100, 101))
    }
}
