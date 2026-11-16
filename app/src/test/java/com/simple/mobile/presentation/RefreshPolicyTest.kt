package com.simple.mobile.presentation

import com.simple.mobile.data.local.EncryptedReadingStore.Entry
import com.simple.mobile.domain.carrier.CubacelQuery
import com.simple.mobile.domain.model.CarrierReading
import org.junit.Assert.*
import org.junit.Test

class RefreshPolicyTest {
    private val now = 1_800_000_000_000L
    private fun balance(at: Long = now, amount: String? = "10", expiry: Long? = null, recharge: String? = null, sub: Int = 1) =
        Entry(sub, CarrierReading(CubacelQuery.BALANCE, at, amount, null, null, emptyList(), expiry, recharge, null))
    @Test fun freshReadingsOnlySkipTheirOwnSimAndCategory() {
        val needed = RefreshPolicy.needed(listOf(balance()), 1, now)
        assertFalse(CubacelQuery.BALANCE in needed)
        assertEquals(5, needed.size)
        assertEquals(RefreshSequence.ALL, RefreshPolicy.needed(listOf(balance()), 2, now))
    }
    @Test fun freshnessBoundaryAndExpiredQuotaMustBeQueried() {
        assertTrue(CubacelQuery.BALANCE in RefreshPolicy.needed(listOf(balance(now - RefreshPolicy.FRESH_FOR)), 1, now))
        assertTrue(CubacelQuery.BALANCE in RefreshPolicy.needed(listOf(balance(expiry = now)), 1, now))
        assertFalse(CubacelQuery.BALANCE in RefreshPolicy.needed(listOf(balance(now - RefreshPolicy.FRESH_FOR + 1)), 1, now))
    }
    @Test fun unknownFutureAndReceiptValuesNeverCountAsFreshMeasurements() {
        assertTrue(CubacelQuery.BALANCE in RefreshPolicy.needed(listOf(balance(amount = null)), 1, now))
        assertTrue(CubacelQuery.BALANCE in RefreshPolicy.needed(listOf(balance(now + 1)), 1, now))
        assertTrue(CubacelQuery.BALANCE in RefreshPolicy.needed(listOf(balance(now - 100), balance(recharge = "100")), 1, now))
    }
    @Test fun retryKeepsFailedAndUnattemptedQueriesInOrder() {
        assertEquals(RefreshSequence.ALL.drop(2), RefreshPolicy.remaining(RefreshSequence.ALL, RefreshSequence.ALL.take(2).toSet()))
        assertTrue(RefreshPolicy.remaining(listOf(CubacelQuery.DATA), setOf(CubacelQuery.DATA)).isEmpty())
    }
}
