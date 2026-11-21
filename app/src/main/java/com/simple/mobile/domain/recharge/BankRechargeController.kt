package com.simple.mobile.domain.recharge

import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BankPhase { AUTH_SENDING, AUTH_RESPONSE, AUTH_UNKNOWN, AUTH_REQUIRED, SENDING, RESPONSE, UNKNOWN, NOT_SENT }
data class BankAttempt(val id: String, val subscription: Int, val line: String, val phone: String,
    val cup: String, val at: Long, val phase: BankPhase)
data class BankState(val ready: Boolean = false, val busy: Boolean = false, val attempt: BankAttempt? = null,
    val response: String? = null, val error: String? = null)
interface BankAttemptStore {
    fun load(): BankAttempt?
    fun save(attempt: BankAttempt)
    fun clear()
}
class BankNotSentException : Exception()

/** App-scoped, durable intent before either request. No automatic transitions from auth to debit.
 * No PIN retained in state/storage, no automatic retry, and no inference of payment success.
 */
class BankRechargeController(private val store: BankAttemptStore, private val worker: Executor,
    private val authenticate: (Int, CharArray) -> CompletableFuture<String>,
    private val recharge: (Int, String, String) -> CompletableFuture<String>,
    private val clock: () -> Long = System::currentTimeMillis) {
    private val mutable = MutableStateFlow(BankState())
    val state = mutable.asStateFlow()
    init { worker.execute {
        try {
            val previous = store.load()?.let { a -> a.copy(phase = when(a.phase) {
                BankPhase.SENDING -> BankPhase.UNKNOWN
                BankPhase.AUTH_SENDING, BankPhase.AUTH_RESPONSE, BankPhase.AUTH_UNKNOWN -> BankPhase.AUTH_REQUIRED
                else -> a.phase
            }) }
            mutable.value = BankState(ready = true, attempt = previous)
        } catch (_: Exception) { mutable.value = BankState(error = "No se pudo abrir el registro de recarga. Reabre SIMple antes de continuar.") }
    } }
    @Synchronized fun authenticate(subscription: Int, line: String, phone: String, cup: String, pin: CharArray) {
        val current = mutable.value
        if (!current.ready || current.busy || current.attempt != null || subscription < 0 ||
            MetropolitanoProtocol.phone(phone) != phone || MetropolitanoProtocol.amount(cup) != cup || !MetropolitanoProtocol.validPin(pin)) {
            pin.fill('\u0000'); return
        }
        val attempt = BankAttempt(UUID.randomUUID().toString(), subscription, line, phone, cup, clock(), BankPhase.AUTH_SENDING)
        mutable.value = current.copy(busy = true, attempt = attempt, error = null, response = null)
        worker.execute {
            try {
                store.save(attempt)
                send(attempt, false) { authenticate(subscription, pin) }
            } catch (_: Exception) {
                mutable.value = BankState(ready = true, error = "No se pudo guardar el registro. No se envió autenticación.")
            } finally { pin.fill('\u0000') }
        }
    }
    /** Refresh the visible authentication state without sending any bank request. */
    @Synchronized fun refreshAuthenticationExpiry() {
        val current = mutable.value
        val attempt = current.attempt ?: return
        if (current.busy || attempt.phase != BankPhase.AUTH_RESPONSE) return
        if (clock() - attempt.at !in 0..300_000L) {
            mutable.value = current.copy(attempt = attempt.copy(phase = BankPhase.AUTH_REQUIRED),
                error = "La autenticación venció. Revisa la respuesta y vuelve a autenticarte.")
        }
    }
    @Synchronized fun confirm(attemptId: String, authenticationConfirmed: Boolean) {
        refreshAuthenticationExpiry()
        val current = mutable.value
        val attempt = current.attempt ?: return
        if (!current.ready || current.busy || attempt.id != attemptId || attempt.phase != BankPhase.AUTH_RESPONSE || !authenticationConfirmed) return
        val sending = attempt.copy(phase = BankPhase.SENDING)
        mutable.value = current.copy(busy = true, attempt = sending, response = null, error = null)
        worker.execute {
            try { store.save(sending) }
            catch (_: Exception) {
                mutable.value = current.copy(error = "No se pudo guardar la solicitud. No se envió la recarga."); return@execute
            }
            send(sending, true) { recharge(sending.subscription, sending.phone, sending.cup) }
        }
    }
    private fun send(attempt: BankAttempt, debit: Boolean, request: () -> CompletableFuture<String>) {
        try {
            request().whenComplete { response, failure -> worker.execute {
                var cause = failure
                while (cause?.cause != null) cause = cause.cause
                val phase = when {
                    cause is BankNotSentException -> BankPhase.NOT_SENT
                    cause != null || response.isNullOrBlank() -> if (debit) BankPhase.UNKNOWN else BankPhase.AUTH_UNKNOWN
                    else -> if (debit) BankPhase.RESPONSE else BankPhase.AUTH_RESPONSE
                }
                finish(attempt.copy(phase = phase), response?.takeIf { phase in setOf(BankPhase.AUTH_RESPONSE, BankPhase.RESPONSE) })
            } }
        } catch (_: Exception) { finish(attempt.copy(phase = if (debit) BankPhase.UNKNOWN else BankPhase.AUTH_UNKNOWN), null) }
    }
    private fun finish(attempt: BankAttempt, response: String?) {
        var error: String? = null
        try { store.save(attempt) }
        catch (_: Exception) { error = "No se pudo guardar el resultado. Comprueba los SMS antes de repetir una operación." }
        mutable.value = BankState(ready = true, attempt = attempt, response = response, error = error)
    }
    @Synchronized fun acknowledge() {
        if (!mutable.value.ready || mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true)
        worker.execute {
            try { store.clear(); mutable.value = BankState(ready = true) }
            catch (_: Exception) { mutable.value = mutable.value.copy(busy = false, error = "No se pudo cerrar el registro de recarga.") }
        }
    }
    @Synchronized fun clearForLine(subscription: Int?) {
        check(!mutable.value.busy)
        if (subscription == null || mutable.value.attempt?.subscription == subscription) {
            store.clear(); mutable.value = BankState(ready = true)
        }
    }
}
