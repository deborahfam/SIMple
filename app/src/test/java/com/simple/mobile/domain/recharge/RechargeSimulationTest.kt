package com.simple.mobile.domain.recharge

import com.simple.mobile.domain.recharge.RechargeSimulation as D
import org.junit.Assert.*
import org.junit.Test

class RechargeSimulationTest {
    private fun draft() = D.prepare(7, "SIM 2", D.Bank.METRO, "55500000", "100", 1_000)
    private fun auth() = D.authenticate(draft(), D.DEMO_PIN)
    private fun pending(): D.Attempt { val a = auth(); return D.receive(a, D.fixture(a, D.Kind.AUTH_OK, 2_000)) }
    @Test fun validatesMoneyWithoutRoundingOrScientificNotation() {
        listOf("", "0", "-1", "NaN", "1e3", "1.001", "1000000", "1,2.3").forEach { assertNull(it, D.amount(it)) }
        assertEquals("12.50", D.amount("12,5"))
        assertEquals("999999.99", D.amount("999999.99"))
        assertEquals("0.01", D.amount("0.01"))
    }
    @Test fun requiresSimAndPlausibleTestNumber() {
        assertThrows(IllegalArgumentException::class.java) { D.prepare(-1, "", D.Bank.METRO, "55500000", "1", 0) }
        assertThrows(IllegalArgumentException::class.java) { D.prepare(1, "", D.Bank.METRO, "11111111", "1", 0) }
        assertThrows(IllegalArgumentException::class.java) { D.prepare(1, "", D.Bank.METRO, "55500000", "0", 0) }
    }
    @Test fun wrongPinNeverSends() {
        val a = D.authenticate(draft(), "0000")
        assertEquals(D.Phase.AUTH_REJECTED, a.phase)
        assertEquals(0, a.simulatedSends)
        assertEquals(a, D.receive(a, D.fixture(a, D.Kind.AUTH_OK, 2_000)))
    }
    @Test fun reviewAndPendingAuthAreNotPayments() {
        val a = draft()
        assertEquals(a, D.receive(a, D.fixture(a, D.Kind.RECHARGE_OK, 2_000)))
        val b = D.authenticate(a, D.DEMO_PIN)
        assertEquals(0, b.simulatedSends)
        assertEquals(b, D.receive(b, D.fixture(b, D.Kind.RECHARGE_OK, 2_000)))
    }
    @Test fun authenticationAndConfirmationAreIdempotent() {
        val a = auth()
        val authMessage = D.fixture(a, D.Kind.AUTH_OK, 2_000)
        val sent = D.receive(a, authMessage)
        repeat(10) { assertEquals(sent, D.receive(sent, authMessage)); assertEquals(sent, D.authenticate(sent, D.DEMO_PIN)) }
        val receipt = D.fixture(sent, D.Kind.RECHARGE_OK, 3_000)
        val done = D.receive(sent, receipt)
        assertEquals(D.Phase.SUCCESS, done.phase)
        assertEquals(1, done.simulatedSends)
        repeat(10) { assertEquals(done, D.receive(done, receipt)) }
    }
    @Test fun rejectsMessagesForOtherSimBankPhoneAmountOrAttempt() {
        val a = pending(); val m = D.fixture(a, D.Kind.RECHARGE_OK, 3_000)
        listOf(m.copy(sender = "PAGOxMOVIL"), m.copy(attemptId = "other"), m.copy(subscription = 8),
            m.copy(bank = D.Bank.BANDEC), m.copy(phone = "55500001"), m.copy(cup = "101"),
            m.copy(at = 999), m.copy(at = a.createdAt + D.WINDOW_MS + 1), m.copy(reference = "")).forEach {
            assertEquals(a, D.receive(a, it))
        }
    }
    @Test fun expiredAuthenticationCannotSend() {
        val a = D.timeout(auth())
        assertEquals(D.Phase.EXPIRED, a.phase)
        assertEquals(0, a.simulatedSends)
        assertEquals(a, D.receive(a, D.fixture(a, D.Kind.AUTH_OK, 3_000)))
    }
    @Test fun lostReplyIsUncertainAndDoesNotRetry() {
        val a = D.timeout(pending())
        assertEquals(D.Phase.UNCERTAIN, a.phase)
        assertEquals(1, a.simulatedSends)
        assertEquals(a, D.authenticate(a, D.DEMO_PIN))
        assertEquals(a, D.receive(a, D.fixture(a, D.Kind.AUTH_OK, 3_000)))
        assertEquals(D.Phase.SUCCESS, D.receive(a, D.fixture(a, D.Kind.RECHARGE_OK, 3_000)).phase)
    }
    @Test fun restartCannotResumeSending() {
        assertEquals(D.Phase.EXPIRED, D.restore(auth()).phase)
        val a = D.restore(pending())
        assertEquals(D.Phase.UNCERTAIN, a.phase)
        assertEquals(1, a.simulatedSends)
        assertEquals(a, D.restore(a))
    }
    @Test fun sessionExpiryDistinguishesBeforeAndAfterSend() {
        val before = auth(); val after = pending()
        assertEquals(D.Phase.EXPIRED, D.receive(before, D.fixture(before, D.Kind.SESSION_EXPIRED, 3_000)).phase)
        assertEquals(D.Phase.UNCERTAIN, D.receive(after, D.fixture(after, D.Kind.SESSION_EXPIRED, 3_000)).phase)
    }
    @Test fun terminalRejectionAndSuccessCannotBeRewritten() {
        val a = pending()
        val rejected = D.receive(a, D.fixture(a, D.Kind.RECHARGE_REJECTED, 3_000))
        assertEquals(D.Phase.REJECTED, rejected.phase)
        assertEquals(rejected, D.receive(rejected, D.fixture(rejected, D.Kind.RECHARGE_OK, 4_000)))
        val done = D.receive(a, D.fixture(a, D.Kind.RECHARGE_OK, 3_000))
        assertEquals(done, D.receive(done, D.fixture(done, D.Kind.RECHARGE_REJECTED, 4_000)))
    }
    @Test fun newTrialCannotConsumePreviousReceipt() {
        val old = pending()
        val next = D.authenticate(draft(), D.DEMO_PIN)
        assertNotEquals(old.id, next.id)
        assertEquals(next, D.receive(next, D.fixture(old, D.Kind.AUTH_OK, 3_000)))
    }
}
