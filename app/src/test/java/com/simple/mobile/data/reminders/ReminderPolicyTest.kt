package com.simple.mobile.data.reminders

import com.simple.mobile.data.local.EncryptedReadingStore.Entry
import com.simple.mobile.domain.carrier.CubacelQuery
import com.simple.mobile.domain.model.CarrierReading
import org.junit.Assert.*
import org.junit.Test

class ReminderPolicyTest {
    private val now = 10 * ReminderPolicy.DAY
    private fun reading(query: CubacelQuery = CubacelQuery.BALANCE, at: Long = now, balance: String? = "10",
                        voice: Long? = null, sms: Long? = null, bundles: List<CarrierReading.Bundle> = emptyList(),
                        expiry: Long? = null, recharge: Long? = null) =
        CarrierReading(query, at, balance, voice, sms, bundles, expiry, null, null, "USSD", null, null, recharge)
    private fun events(vararg readings: CarrierReading) = ReminderPolicy.events(readings.map { Entry(1, it) }, now - 1)
    private fun plan(events: List<ReminderPolicy.Event>, sent: Set<String> = emptySet(), at: Long = now) =
        ReminderPolicy.plan(events, sent, setOf(1), at)

    @Test fun expirySchedulesOneDayBeforeAndNeverAfterExpiration() {
        val expiry = now + 2 * ReminderPolicy.DAY
        val events = events(reading(balance = "100", expiry = expiry))
        assertEquals(now + ReminderPolicy.DAY, plan(events).next)
        assertEquals(1, plan(events, at = expiry - 1).due.size)
        assertTrue(plan(events, at = expiry).due.isEmpty())
        assertNull(plan(events, at = expiry).next)
    }
    @Test fun rechargePromptsConfirmationAtCarrierDateOnly() {
        val events = events(reading(CubacelQuery.RECHARGE_STATUS, recharge = now + 100))
        assertTrue(plan(events).due.isEmpty())
        val due = plan(events, at = now + 100).due.single()
        assertTrue(due.text.contains("no garantiza disponibilidad"))
    }
    @Test fun oldOrPreOptInLowReadingsDoNotNotify() {
        assertTrue(events(reading(at = now - 2)).isEmpty())
        val events = events(reading())
        assertTrue(plan(events, at = now + ReminderPolicy.DAY).due.isEmpty())
    }
    @Test fun newerRecoveryRearmsLowReminderWithoutRepeatingOngoingLowState() {
        val first = events(reading())
        val delivered = setOf(first.single().key)
        assertTrue(plan(events(reading(at = now + 1)), delivered, now + 1).due.isEmpty())
        val recovered = plan(events(reading(), reading(at = now + 1, balance = "100")), delivered, now + 1)
        assertTrue(recovered.retained.isEmpty())
        assertEquals(delivered, recovered.cancel)
        assertEquals(1, plan(events(reading(at = now + 2)), recovered.retained, now + 2).due.size)
    }
    @Test fun receiptDoesNotHideLastMeasuredBalanceAndSimsStaySeparate() {
        val receipt = reading(at = now + 1, balance = null)
        val events = ReminderPolicy.events(listOf(Entry(1, reading()), Entry(1, receipt), Entry(2, reading())), now - 1)
        assertEquals(2, events.size)
        assertEquals(1, plan(events).due.single().subscription)
        assertTrue(ReminderPolicy.plan(events, emptySet(), emptySet(), now).due.isEmpty())
    }
    @Test fun restrictedAndUnlimitedDataDoNotCauseLowGeneralDataAlerts() {
        val restricted = CarrierReading.Bundle("national", 1, false, null)
        val unlimited = CarrierReading.Bundle("general", null, true, null)
        assertTrue(events(reading(CubacelQuery.DATA, bundles = listOf(restricted))).isEmpty())
        assertTrue(events(reading(CubacelQuery.DATA, bundles = listOf(unlimited))).isEmpty())
        assertEquals(1, events(reading(CubacelQuery.DATA, bundles = listOf(CarrierReading.Bundle("general", 100_000_000, false, null)))).size)
    }
    @Test fun thresholdsIncludeBoundaryAndExcludeHigherValues() {
        assertEquals(1, events(reading(balance = "20")).size)
        assertTrue(events(reading(balance = "20.01")).isEmpty())
        assertEquals(1, events(reading(CubacelQuery.VOICE, voice = 300)).size)
        assertTrue(events(reading(CubacelQuery.VOICE, voice = 301)).isEmpty())
        assertEquals(1, events(reading(CubacelQuery.SMS, sms = 5)).size)
        assertTrue(events(reading(CubacelQuery.SMS, sms = 6)).isEmpty())
    }
    @Test fun clearingHistoryCancelsExistingRemindersAndFutureAlarm() {
        val result = plan(emptyList(), setOf("previous"))
        assertEquals(setOf("previous"), result.cancel)
        assertTrue(result.retained.isEmpty())
        assertTrue(result.due.isEmpty())
        assertNull(result.next)
    }
    @Test fun expiredLowQuotaDoesNotNotifyAndSameExpiryIsDeduplicated() {
        val r = reading(CubacelQuery.DATA, expiry = now,
            bundles = listOf(CarrierReading.Bundle("general", 0, false, now)))
        val events = events(r)
        assertEquals(1, events.count { it.key.startsWith("expiry:") })
        assertTrue(plan(events).due.isEmpty())
    }
}
