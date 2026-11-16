package com.simple.mobile.presentation

import com.simple.mobile.data.local.EncryptedReadingStore.Entry
import com.simple.mobile.domain.carrier.CubacelQuery
import com.simple.mobile.domain.model.CarrierReading
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.math.BigDecimal

class StatisticsPolicyTest {
    private val today = LocalDate.of(2026, 11, 16)
    private val now = today.atTime(12, 0).atZone(StatisticsPolicy.zone).toInstant().toEpochMilli()
    private fun data(at: Long = now, bytes: Long = 1_000_000_000, expiry: Long? = null, unlimited: Boolean = false) =
        Entry(1, CarrierReading(CubacelQuery.DATA, at, null, null, null, listOf(CarrierReading.Bundle("general", bytes.takeUnless { unlimited }, unlimited, expiry)), null, null, null))
    private fun balance(at: Long, value: String?, recharge: String? = null) = Entry(1, CarrierReading(CubacelQuery.BALANCE, at, value, null, null, emptyList(), null, recharge, null))
    @Test fun distributesRemainingGeneralQuotaAcrossCalendarDays() {
        val budget = StatisticsPolicy.budget(listOf(data()), 1, today.plusDays(4), now)
        assertEquals(250_000_000L, budget.dailyBytes)
        assertEquals(4L, budget.days)
        assertNull(StatisticsPolicy.budget(listOf(data()), 2, today.plusDays(4), now).dailyBytes)
    }
    @Test fun noBudgetForPastTodayStaleExpiredOrUnlimited() {
        assertNull(StatisticsPolicy.budget(listOf(data()), 1, today, now).dailyBytes)
        assertNull(StatisticsPolicy.budget(listOf(data()), 1, today.minusDays(1), now).dailyBytes)
        assertNull(StatisticsPolicy.budget(listOf(data(at = now - DashboardInsights.DAY)), 1, today.plusDays(1), now).dailyBytes)
        assertNull(StatisticsPolicy.budget(listOf(data(expiry = now)), 1, today.plusDays(1), now).dailyBytes)
        assertNull(StatisticsPolicy.budget(listOf(data(unlimited = true)), 1, today.plusDays(1), now).dailyBytes)
    }
    @Test fun expiryBeforeTargetDoesNotSuggestPackageCanLast() {
        val result = StatisticsPolicy.budget(listOf(data(expiry = now + DashboardInsights.DAY)), 1, today.plusDays(4), now)
        assertNull(result.dailyBytes)
        assertTrue(result.message.contains("antes"))
    }
    @Test fun zeroIsValidAndFutureReadingsAreIgnored() {
        val budget = StatisticsPolicy.budget(listOf(data(bytes = 0), data(at = now + 1000)), 1, today.plusDays(2), now)
        assertEquals(0L, budget.dailyBytes)
    }
    @Test fun sumsOnlyComparableIntervalsAndScopesPeriod() {
        val entries = listOf(balance(now - 40 * DashboardInsights.DAY, "999"), balance(now - 3000, "100"), balance(now - 2000, "80"), balance(now - 1000, "200"), balance(now, "190"))
        val result = StatisticsPolicy.reductions(entries, 1, 7, now).first()
        assertEquals(BigDecimal("30"), result.amount)
        assertEquals(2, result.intervals)
        assertEquals(1, result.omitted)
        assertNull(StatisticsPolicy.reductions(entries, 2, 7, now).first().amount)
    }
    @Test fun knownRechargeBreaksComparison() {
        val result = StatisticsPolicy.reductions(listOf(balance(now - 2000, "100"), balance(now - 1000, null, "100"), balance(now, "50")), 1, 7, now).first()
        assertNull(result.amount)
        assertEquals(1, result.omitted)
    }
    @Test fun expiredQuotaIsNotCountedAsConsumedData() {
        val entries = listOf(data(at = now - 2000, expiry = now - 1000), data(bytes = 0, expiry = now - 1000))
        assertNull(StatisticsPolicy.reductions(entries, 1, 7, now).first { it.query == CubacelQuery.DATA }.amount)
    }
}
