package com.simple.mobile.domain.plans

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

enum class PurchasePhase { SENDING, RESPONSE, UNKNOWN, NOT_SENT }
data class PlanAttempt(val id: String, val planId: String, val subscription: Int, val at: Long, val phase: PurchasePhase,
    val response: String? = null)
data class PlanPurchaseState(val ready: Boolean = false, val busy: Boolean = false, val attempt: PlanAttempt? = null,
    val response: String? = null, val error: String? = null)
interface PlanAttemptStore {
    fun load(): PlanAttempt?
    fun save(attempt: PlanAttempt)
    fun clear()
}
class PlanNotSentException: Exception()

/** Application-scoped; persists intent BEFORE dispatch, never retries or resumes a send. */
class PlanPurchaseController(private val store: PlanAttemptStore, private val worker: Executor,
    private val send: (Int, String) -> CompletableFuture<String>) {
    private val mutable = MutableStateFlow(PlanPurchaseState())
    val state = mutable.asStateFlow()
    init { worker.execute {
        try {
            val previous = store.load()?.let { if (it.phase == PurchasePhase.SENDING) it.copy(phase = PurchasePhase.UNKNOWN) else it }
            mutable.value = PlanPurchaseState(ready = true, attempt = previous,
                response = previous?.response?.takeIf { previous.phase == PurchasePhase.RESPONSE })
        } catch (_: Exception) { mutable.value = PlanPurchaseState(error = "No se pudo abrir el registro de compra. Cierra y vuelve a abrir SIMple.") }
    } }
    @Synchronized fun confirm(planId: String, subscription: Int) {
        val current = mutable.value
        if (!current.ready || current.busy || current.attempt != null || subscription < 0 || PlanCatalog.codeFor(planId) == null) return
        val attempt = PlanAttempt(UUID.randomUUID().toString(), planId, subscription, System.currentTimeMillis(), PurchasePhase.SENDING)
        mutable.value = current.copy(busy = true, attempt = attempt, error = null)
        worker.execute {
            try { store.save(attempt) }
            catch (_: Exception) {
                mutable.value = PlanPurchaseState(ready = true, error = "No se pudo guardar el registro. No se envió ninguna compra.")
                return@execute
            }
            try {
                send(subscription, planId).whenComplete { response, failure -> worker.execute {
                    var cause = failure
                    while (cause?.cause != null) cause = cause.cause
                    val phase = when {
                        cause is PlanNotSentException -> PurchasePhase.NOT_SENT
                        cause != null || response.isNullOrBlank() -> PurchasePhase.UNKNOWN
                        else -> PurchasePhase.RESPONSE
                    }
                    finish(attempt.copy(phase = phase), response?.takeIf { phase == PurchasePhase.RESPONSE })
                } }
            } catch (_: Exception) { finish(attempt.copy(phase = PurchasePhase.UNKNOWN), null) }
        }
    }
    private fun finish(attempt: PlanAttempt, response: String?) {
        var error: String? = null
        val outcome = attempt.copy(response = response)
        try { store.save(outcome) }
        catch (_: Exception) { error = "No se pudo guardar el resultado. No repitas la compra sin revisar tus recursos." }
        mutable.value = PlanPurchaseState(ready = true, attempt = outcome, response = response, error = error)
    }
    @Synchronized fun acknowledge() {
        if (!mutable.value.ready || mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true)
        worker.execute {
            try { store.clear(); mutable.value = PlanPurchaseState(ready = true) }
            catch (_: Exception) { mutable.value = mutable.value.copy(busy = false, error = "No se pudo cerrar el registro de compra.") }
        }
    }
    /** Called by the existing serialized local-history deletion flow. */
    @Synchronized fun clearForLine(subscription: Int?) {
        check(!mutable.value.busy)
        if (subscription == null || mutable.value.attempt?.subscription == subscription) {
            store.clear()
            mutable.value = PlanPurchaseState(ready = true)
        }
    }
}
