package com.simple.mobile.domain.plans

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

class PlanPurchaseControllerTest {
    private val worker = Executor { it.run() }
    private class MemoryStore : PlanAttemptStore {
        var attempt: PlanAttempt? = null
        var failSave = false
        override fun load() = attempt
        override fun save(attempt: PlanAttempt) { check(!failSave); this.attempt = attempt }
        override fun clear() { attempt = null }
    }
    @Test fun menuOptionsMapToExactCommandsAndRejectArbitraryInput() {
        assertEquals(listOf("*133*1*4*1*1#", "*133*1*4*2*1#", "*133*1*4*3*1#", "*133*1*4*4*1#"), PlanCatalog.plans.map { PlanCatalog.codeFor(it.id) })
        assertEquals(listOf(240,120,240,360), PlanCatalog.plans.map { it.priceCup })
        assertNull(PlanCatalog.codeFor("*133#"))
        assertNull(PlanCatalog.codeFor("daily_200mb"))
    }
    @Test fun persistsBeforeSendingAndDoubleTapCannotSendTwice() {
        val store = MemoryStore(); val pending = CompletableFuture<String>(); var sends = 0
        val controller = PlanPurchaseController(store, worker) { sub, id ->
            assertEquals(7, sub); assertEquals("combo_2gb", id)
            assertEquals(PurchasePhase.SENDING, store.attempt?.phase)
            sends++; pending
        }
        controller.confirm("combo_2gb", 7); controller.confirm("combo_6gb", 8)
        controller.acknowledge()
        assertEquals(1, sends)
        pending.complete("Solicitud recibida")
        assertEquals(PurchasePhase.RESPONSE, controller.state.value.attempt?.phase)
        assertEquals("Solicitud recibida", controller.state.value.response)
        controller.confirm("combo_6gb", 8)
        assertEquals(1, sends)
    }
    @Test fun storageFailurePreventsAnyNetworkRequest() {
        val store = MemoryStore().apply { failSave = true }; var sends = 0
        val controller = PlanPurchaseController(store, worker) { _, _ -> sends++; CompletableFuture.completedFuture("ok") }
        controller.confirm("combo_2gb", 7)
        assertEquals(0, sends); assertNull(controller.state.value.attempt)
        assertNotNull(controller.state.value.error)
    }
    @Test fun restartOfPendingAttemptNeverReplaysIt() {
        val store = MemoryStore().apply { attempt = PlanAttempt("old", "combo_2gb", 7, 10, PurchasePhase.SENDING) }
        var sends = 0
        val controller = PlanPurchaseController(store, worker) { _, _ -> sends++; CompletableFuture.completedFuture("ok") }
        controller.confirm("combo_2gb", 7)
        assertEquals(0, sends); assertEquals(PurchasePhase.UNKNOWN, controller.state.value.attempt?.phase)
        assertNull(controller.state.value.response)
    }
    @Test fun timeoutIsUncertainAndDoesNotRetry() {
        val pending = CompletableFuture<String>(); var sends = 0
        val controller = PlanPurchaseController(MemoryStore(), worker) { _, _ -> sends++; pending }
        controller.confirm("combo_2gb", 7)
        pending.completeExceptionally(IllegalStateException("timeout"))
        assertEquals(PurchasePhase.UNKNOWN, controller.state.value.attempt?.phase)
        assertEquals(1, sends)
    }
    @Test fun permissionFailureIsNotSentAndRequiresNewReview() {
        val controller = PlanPurchaseController(MemoryStore(), worker) { _, _ -> CompletableFuture<String>().apply { completeExceptionally(PlanNotSentException()) } }
        controller.confirm("combo_2gb", 7)
        assertEquals(PurchasePhase.NOT_SENT, controller.state.value.attempt?.phase)
        controller.acknowledge()
        assertNull(controller.state.value.attempt)
    }
    @Test fun deletionDoesNotForgetOtherLineAndCannotClearPendingSend() {
        val pending = CompletableFuture<String>()
        val controller = PlanPurchaseController(MemoryStore(), worker) { _, _ -> pending }
        controller.confirm("combo_2gb", 7)
        assertThrows(IllegalStateException::class.java) { controller.clearForLine(null) }
        pending.complete("Respuesta")
        controller.clearForLine(8)
        assertNotNull(controller.state.value.attempt)
        controller.clearForLine(7)
        assertNull(controller.state.value.attempt)
    }
}
