package com.simple.mobile.presentation

import com.simple.mobile.data.local.EncryptedReadingStore.Entry
import com.simple.mobile.domain.carrier.CubacelQuery

/** Recent means measured within 15 minutes; unknown or expired resources are always queried. */
internal object RefreshPolicy {
    const val FRESH_FOR = 15 * 60_000L
    fun needed(entries: List<Entry>, subscription: Int, now: Long): List<CubacelQuery> {
        val readings = entries.filter { it.subscription == subscription && it.reading.receivedAt() <= now }.map { it.reading }
        return RefreshSequence.ALL.filter { query ->
            val latest = readings.filter { it.query() == query && it.purchase() == null && it.receivedRechargeCup() == null && when (query) {
                    CubacelQuery.BALANCE -> it.balanceCup() != null
                    CubacelQuery.DATA, CubacelQuery.BONUSES_AND_USD_PLANS -> it.bundles().isNotEmpty()
                    CubacelQuery.VOICE -> it.voiceSeconds() != null
                    CubacelQuery.SMS -> it.smsCount() != null
                    CubacelQuery.RECHARGE_STATUS -> it.availableRechargeCup() != null || it.nextRechargeAt() != null
                } }
                .maxByOrNull { it.receivedAt() }
            latest == null || now - latest.receivedAt() >= FRESH_FOR ||
                (listOfNotNull(latest.expiresAt()) + latest.bundles().mapNotNull { it.expiresAt() }).any { it <= now } ||
                readings.any { it.receivedAt() >= latest.receivedAt() && (it.purchase() != null || it.receivedRechargeCup() != null) }
        }
    }
    fun remaining(requested: List<CubacelQuery>, completed: Set<CubacelQuery>) = requested.filterNot { it in completed }
}
