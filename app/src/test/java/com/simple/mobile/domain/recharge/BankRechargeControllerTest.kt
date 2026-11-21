package com.simple.mobile.domain.recharge

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Test

class BankRechargeControllerTest {
    private class Store : BankAttemptStore {
        var value: BankAttempt? = null
        var fail = false
        override fun load() = value
        override fun save(attempt: BankAttempt) { check(!fail); value = attempt }
        override fun clear() { check(!fail); value = null }
    }
    private class Fixture(val store: Store = Store()) {
        var authCount = 0
        var sends = 0
        var now = 1000L
        val auth = CompletableFuture<String>()
        val debit = CompletableFuture<String>()
        val controller = BankRechargeController(store, Executor { it.run() }, { sub, pin ->
            assertEquals(7, sub); assertEquals(BankPhase.AUTH_SENDING, store.value?.phase)
            assertEquals("4826", String(pin)); authCount++; auth
        }, { sub, phone, cup ->
            assertEquals(7, sub); assertEquals("55500000", phone); assertEquals("120", cup)
            assertEquals(BankPhase.SENDING, store.value?.phase); sends++; debit
        }, { now })
        fun start() {
            val pin = "4826".toCharArray()
            controller.authenticate(7, "Mi SIM", "55500000", "120", pin)
            assertTrue(pin.all { it == '\u0000' })
        }
        fun confirm(ack: Boolean = true) = controller.confirm(controller.state.value.attempt!!.id, ack)
    }
    @Test fun authenticationNeverAutomaticallyDebitsAndDoubleTapsCannotReplay() {
        val f = Fixture(); f.start(); f.start(); assertEquals(1, f.authCount)
        f.auth.complete("Solicitud recibida"); assertEquals(0, f.sends)
        f.confirm(false); assertEquals(0, f.sends)
        f.confirm(); f.confirm(); assertEquals(1, f.sends)
        f.debit.complete("Solicitud recibida")
        assertEquals(BankPhase.RESPONSE, f.controller.state.value.attempt?.phase)
        f.confirm(); assertEquals(1, f.sends)
    }
    @Test fun storageFailureBlocksAuthenticationAndDebit() {
        val f = Fixture(); f.store.fail = true; f.start(); assertEquals(0, f.authCount)
        f.store.fail = false; f.start(); f.auth.complete("Respuesta")
        f.store.fail = true; f.confirm(); assertEquals(0, f.sends)
    }
    @Test fun restartNeverResumesChargeOrAuthentication() {
        val f = Fixture(); f.start(); f.auth.complete("Respuesta"); f.confirm()
        val restored = Fixture(f.store)
        assertEquals(BankPhase.UNKNOWN, restored.controller.state.value.attempt?.phase)
        assertEquals(0, restored.sends); assertEquals(0, restored.authCount)
        val authOnly = Fixture(); authOnly.start(); authOnly.auth.complete("Respuesta")
        val second = Fixture(authOnly.store)
        assertEquals(BankPhase.AUTH_REQUIRED, second.controller.state.value.attempt?.phase)
        second.confirm(); assertEquals(0, second.sends)
    }
    @Test fun failureDoesNotImplyNoDebitAndExplicitPreflightFailureDoes() {
        val f = Fixture(); f.start(); f.auth.complete("Respuesta"); f.confirm()
        f.debit.completeExceptionally(IllegalStateException())
        assertEquals(BankPhase.UNKNOWN, f.controller.state.value.attempt?.phase)
        val g = Fixture(); g.start(); g.auth.complete("Respuesta"); g.confirm()
        g.debit.completeExceptionally(BankNotSentException())
        assertEquals(BankPhase.NOT_SENT, g.controller.state.value.attempt?.phase)
    }
    @Test fun expiredReviewAndInvalidPhoneNeverSend() {
        val f = Fixture(); f.start(); f.auth.complete("Respuesta"); f.now += 300001
        f.confirm(); assertEquals(0, f.sends)
        assertEquals(BankPhase.AUTH_REQUIRED, f.controller.state.value.attempt?.phase)
        val g = Fixture(); val pin = "4826".toCharArray()
        g.controller.authenticate(7, "SIM", "55500000*54", "120", pin)
        assertEquals(0, g.authCount); assertTrue(pin.all { it == '\u0000' })
    }
    @Test fun storageFailureAfterDebitStillBlocksReplay() {
        val f = Fixture(); f.start(); f.auth.complete("Respuesta"); f.confirm()
        f.store.fail = true; f.debit.complete("Respuesta")
        assertNotNull(f.controller.state.value.error)
        f.confirm(); assertEquals(1, f.sends)
        f.controller.acknowledge(); assertNotNull(f.controller.state.value.attempt)
    }
    @Test fun visibleAuthenticationExpiresWithoutPressingConfirm() {
        val f = Fixture(); f.start(); f.auth.complete("Respuesta")
        f.now += 300000
        f.controller.refreshAuthenticationExpiry()
        assertEquals(BankPhase.AUTH_RESPONSE, f.controller.state.value.attempt?.phase)
        f.now++
        f.controller.refreshAuthenticationExpiry()
        assertEquals(BankPhase.AUTH_REQUIRED, f.controller.state.value.attempt?.phase)
        assertNotNull(f.controller.state.value.error)
        assertEquals(7, f.controller.state.value.attempt?.subscription)
        assertEquals(0, f.sends)
        f.confirm()
        assertEquals(0, f.sends)
    }
    @Test fun expiryRefreshDoesNotChangeAnAlreadySentRecharge() {
        val f = Fixture(); f.start(); f.auth.complete("Respuesta"); f.confirm()
        f.now += 300001
        f.controller.refreshAuthenticationExpiry()
        assertEquals(BankPhase.SENDING, f.controller.state.value.attempt?.phase)
        assertTrue(f.controller.state.value.busy)
        assertEquals(1, f.sends)
        f.debit.complete("Recarga recibida")
        f.controller.refreshAuthenticationExpiry()
        assertEquals(BankPhase.RESPONSE, f.controller.state.value.attempt?.phase)
        assertEquals("Recarga recibida", f.controller.state.value.response)
    }
    @Test fun backwardsClockRequiresNewAuthentication() {
        val f = Fixture(); f.start(); f.auth.complete("Respuesta")
        f.now--
        f.controller.refreshAuthenticationExpiry()
        assertEquals(BankPhase.AUTH_REQUIRED, f.controller.state.value.attempt?.phase)
        assertEquals(0, f.sends)
    }

}
