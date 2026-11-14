package com.simple.mobile.presentation

import com.simple.mobile.domain.carrier.CubacelQuery

/** Each query must finish and persist before another is sent. Failures abort the sequence. */
internal object RefreshSequence {
    val ALL = listOf(CubacelQuery.BALANCE, CubacelQuery.DATA, CubacelQuery.VOICE,
        CubacelQuery.SMS, CubacelQuery.BONUSES_AND_USD_PLANS, CubacelQuery.RECHARGE_STATUS)

    suspend fun run(queries: List<CubacelQuery>, stopped: () -> Boolean,
                    execute: suspend (CubacelQuery) -> Unit) {
        for (query in queries) {
            if (stopped()) break
            execute(query)
        }
    }
}
