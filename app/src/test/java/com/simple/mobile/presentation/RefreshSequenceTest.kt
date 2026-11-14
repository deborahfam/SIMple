package com.simple.mobile.presentation

import com.simple.mobile.domain.carrier.CubacelQuery
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class RefreshSequenceTest {
    @Test fun waitsForCurrentQueryBeforeSendingNext() = runBlocking {
        val firstResponse = CompletableDeferred<Unit>()
        val sent = mutableListOf<CubacelQuery>()
        val job = launch {
            RefreshSequence.run(RefreshSequence.ALL, { false }) {
                sent += it
                if (it == CubacelQuery.BALANCE) firstResponse.await()
            }
        }
        yield()
        assertEquals(listOf(CubacelQuery.BALANCE), sent)
        firstResponse.complete(Unit)
        job.join()
        assertEquals(RefreshSequence.ALL, sent)
        assertEquals(CubacelQuery.values().toSet(), sent.toSet())
    }

    @Test fun stopLetsCurrentQueryFinishButSkipsRemainingQueries() = runBlocking {
        val response = CompletableDeferred<Unit>()
        var stopped = false
        var saved = 0
        val sent = mutableListOf<CubacelQuery>()
        val job = launch {
            RefreshSequence.run(RefreshSequence.ALL, { stopped }) {
                sent += it
                response.await()
                saved++
            }
        }
        yield()
        stopped = true
        response.complete(Unit)
        job.join()
        assertEquals(1, saved)
        assertEquals(listOf(CubacelQuery.BALANCE), sent)
    }

    @Test fun failureStopsWithoutRetryingOrSendingRemainingQueries() = runBlocking {
        val sent = mutableListOf<CubacelQuery>()
        try {
            RefreshSequence.run(RefreshSequence.ALL, { false }) {
                sent += it
                if (it == CubacelQuery.DATA) throw IllegalStateException("query failed")
            }
            fail("Failure should propagate")
        } catch (expected: IllegalStateException) {
            assertEquals("query failed", expected.message)
        }
        assertEquals(listOf(CubacelQuery.BALANCE, CubacelQuery.DATA), sent)
    }
}
