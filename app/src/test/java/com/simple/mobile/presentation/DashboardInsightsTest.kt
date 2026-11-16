package com.simple.mobile.presentation

import com.simple.mobile.data.local.EncryptedReadingStore.Entry
import com.simple.mobile.domain.carrier.CubacelQuery
import com.simple.mobile.domain.model.CarrierReading
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class DashboardInsightsTest {
    private val now = 10 * DashboardInsights.DAY
    private fun balance(at: Long, amount: String?, expiry: Long? = null, recharge: String? = null) =
        CarrierReading(CubacelQuery.BALANCE, at, amount, null, null, emptyList(), expiry, recharge, null)
    private fun data(at: Long, bytes: Long?, expiry: Long? = null, unlimited: Boolean = false) =
        CarrierReading(CubacelQuery.DATA, at, null, null, null,
            listOf(CarrierReading.Bundle("general", bytes, unlimited, expiry)), null, null, null)
    private fun entries(vararg readings: CarrierReading) = readings.map { Entry(1, it) }
    @Test fun scopesNoticesAndTrendsToSelectedSim() {
        val values = entries(balance(now - 1000, "50"), balance(now, "10")) + Entry(2, balance(now, "999"))
        assertEquals(BigDecimal("40"), DashboardInsights.trends(values, 1, now).single().reduction)
        assertTrue(DashboardInsights.notices(values, 1, now).any { it.title == "Saldo: nivel bajo" })
        assertFalse(DashboardInsights.notices(values, 2, now).any { it.title == "Saldo: nivel bajo" })
        assertTrue(DashboardInsights.trends(values, 2, now).isEmpty())
    }
    @Test fun staleLowReadingRequestsRefreshInsteadOfClaimingLowBalance() {
        val notices = DashboardInsights.notices(entries(balance(now - DashboardInsights.DAY, "1")), 1, now)
        assertFalse(notices.any { it.title.contains("nivel bajo") })
        assertTrue(notices.any { it.title == "Actualiza tus lecturas" })
    }
    @Test fun exactExpiryBoundaryIsExpiredAndTomorrowIsUpcoming() {
        assertTrue(DashboardInsights.notices(entries(data(now, 500_000_000, now)), 1, now).any { it.title.contains("terminada") })
        assertTrue(DashboardInsights.notices(entries(data(now, 500_000_000, now + DashboardInsights.DAY)), 1, now).any { it.title.contains("24 h") })
    }
    @Test fun unlimitedIsNotLowOrMissingAndIsNotComparedToFiniteData() {
        val values = entries(data(now - 1000, 500_000_000), data(now, null, unlimited = true))
        assertFalse(DashboardInsights.notices(values, 1, now).any { it.query == CubacelQuery.DATA })
        assertTrue(DashboardInsights.trends(values, 1, now).isEmpty())
    }
    @Test fun rechargeBetweenReadingsSuppressesReduction() {
        val values = entries(balance(now - 2000, "50"), balance(now - 1000, null, recharge = "100"), balance(now, "40"))
        assertNull(DashboardInsights.trends(values, 1, now).single().reduction)
    }
    @Test fun purchaseBetweenReadingsSuppressesReduction() {
        val purchase = balance(now - 1000, null).asSms(CarrierReading.Purchase(1_000_000_000, null, null, null, 30, true), null)
        val values = entries(data(now - 2000, 500_000_000), purchase, data(now, 200_000_000))
        assertNull(DashboardInsights.trends(values, 1, now).single().reduction)
    }
    @Test fun increasedResourceAndChangedExpiryAreNotConsumption() {
        assertNull(DashboardInsights.trends(entries(balance(now - 1000, "20"), balance(now, "30")), 1, now).single().reduction)
        assertNull(DashboardInsights.trends(entries(data(now - 1000, 500_000_000, now + 1000), data(now, 200_000_000, now + 2000)), 1, now).single().reduction)
    }
    @Test fun ignoresFutureAndOldReadings() {
        val values = entries(balance(now - 8 * DashboardInsights.DAY, "100"), balance(now, "50"), balance(now + 1000, "10"))
        assertTrue(DashboardInsights.trends(values, 1, now).isEmpty())
        assertFalse(DashboardInsights.notices(values, 1, now).any { it.title.contains("nivel bajo") })
    }
    @Test fun showsExactDataReductionWithoutInventingDailyRate() {
        val trend = DashboardInsights.trends(entries(data(now - 1000, 500_000_000), data(now, 200_000_000)), 1, now).single()
        assertEquals(0, BigDecimal("0.3").compareTo(trend.reduction))
    }
    @Test fun expiredAllowanceCannotBecomeConsumption() {
        assertNull(DashboardInsights.trends(entries(data(now - 2000, 500_000_000, now - 1000), data(now, 0, now - 1000)), 1, now).single().reduction)
    }
}
