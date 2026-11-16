package com.simple.mobile.domain.recharge

import java.math.BigDecimal
import java.util.UUID

/** Offline only. This type has no transport, USSD command, Android context or real SMS adapter. */
object RechargeSimulation {
    const val DEMO_PIN = "1234"
    const val SENDER = "SIMPLE-DEMO"
    const val WINDOW_MS = 5 * 60_000L
    enum class Bank(val label: String) { METRO("Metropolitano"), BPA("BPA"), BANDEC("BANDEC") }
    enum class Phase { REVIEW, AUTH_PENDING, RESULT_PENDING, SUCCESS, AUTH_REJECTED, REJECTED, EXPIRED, UNCERTAIN }
    data class Attempt(val id: String, val subscription: Int, val line: String, val bank: Bank,
        val phone: String, val cup: String, val createdAt: Long, val phase: Phase = Phase.REVIEW,
        val simulatedSends: Int = 0, val resultReference: String? = null)
    enum class Kind { AUTH_OK, AUTH_REJECTED, RECHARGE_OK, RECHARGE_REJECTED, SESSION_EXPIRED }
    data class Message(val sender: String, val attemptId: String, val subscription: Int, val bank: Bank,
        val phone: String, val cup: String, val at: Long, val kind: Kind, val reference: String = "DEMO-RECEIPT")

    fun amount(input: String): String? {
        val cleaned = input.trim().replace(',', '.')
        if (!Regex("[0-9]{1,6}(\\.[0-9]{1,2})?").matches(cleaned)) return null
        val value = cleaned.toBigDecimalOrNull() ?: return null
        return value.takeIf { it > BigDecimal.ZERO && it <= BigDecimal("999999.99") }?.setScale(2)?.toPlainString()
    }
    fun prepare(subscription: Int, line: String, bank: Bank, phone: String, cup: String, now: Long): Attempt {
        require(subscription >= 0) { "Selecciona una SIM para la prueba." }
        require(Regex("5[0-9]{7}").matches(phone)) { "Usa un número de prueba de 8 dígitos que empiece por 5." }
        return Attempt(UUID.randomUUID().toString(), subscription, line, bank, phone,
            requireNotNull(amount(cup)) { "Escribe un importe positivo en CUP, con hasta dos decimales." }, now)
    }
    fun authenticate(a: Attempt, pin: String): Attempt = if (a.phase != Phase.REVIEW) a else
        a.copy(phase = if (pin == DEMO_PIN) Phase.AUTH_PENDING else Phase.AUTH_REJECTED)
    fun receive(a: Attempt, m: Message): Attempt {
        if (m.sender != SENDER || m.attemptId != a.id || m.subscription != a.subscription || m.bank != a.bank ||
            m.phone != a.phone || amount(m.cup) != a.cup || m.at < a.createdAt || m.at - a.createdAt > WINDOW_MS) return a
        return when {
            a.phase == Phase.AUTH_PENDING && m.kind == Kind.AUTH_OK -> a.copy(phase = Phase.RESULT_PENDING, simulatedSends = 1)
            a.phase == Phase.AUTH_PENDING && m.kind == Kind.AUTH_REJECTED -> a.copy(phase = Phase.AUTH_REJECTED)
            a.phase in setOf(Phase.AUTH_PENDING, Phase.RESULT_PENDING) && m.kind == Kind.SESSION_EXPIRED ->
                a.copy(phase = if (a.simulatedSends == 0) Phase.EXPIRED else Phase.UNCERTAIN)
            a.phase in setOf(Phase.RESULT_PENDING, Phase.UNCERTAIN) && a.simulatedSends == 1 && m.kind == Kind.RECHARGE_OK && m.reference.isNotBlank() ->
                a.copy(phase = Phase.SUCCESS, resultReference = m.reference)
            a.phase in setOf(Phase.RESULT_PENDING, Phase.UNCERTAIN) && a.simulatedSends == 1 && m.kind == Kind.RECHARGE_REJECTED -> a.copy(phase = Phase.REJECTED)
            else -> a
        }
    }
    fun timeout(a: Attempt): Attempt = when (a.phase) {
        Phase.AUTH_PENDING -> a.copy(phase = Phase.EXPIRED)
        Phase.RESULT_PENDING -> a.copy(phase = Phase.UNCERTAIN)
        else -> a
    }
    /** Reopening cannot authenticate or send again. A late matching simulated receipt may settle it. */
    fun restore(a: Attempt): Attempt = timeout(a)
    fun fixture(a: Attempt, kind: Kind, now: Long): Message = Message(SENDER, a.id, a.subscription, a.bank, a.phone, a.cup, now, kind)
}
