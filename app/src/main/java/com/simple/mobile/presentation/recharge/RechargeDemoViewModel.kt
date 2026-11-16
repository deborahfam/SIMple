package com.simple.mobile.presentation.recharge

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simple.mobile.data.recharge.SimulationStore
import com.simple.mobile.domain.recharge.RechargeSimulation as Demo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DemoState(val open: Boolean = false, val busy: Boolean = true, val subscription: Int = -1,
    val line: String = "", val attempt: Demo.Attempt? = null, val error: String? = null, val savedPin: Boolean = false)

class RechargeDemoViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SimulationStore(app)
    val vault = com.simple.mobile.data.recharge.DemoPinVault(app)
    private val mutable = MutableStateFlow(DemoState())
    val state = mutable.asStateFlow()
    init {
        viewModelScope.launch {
            try {
                val restored = withContext(Dispatchers.IO) { store.load()?.let { Demo.restore(it).also(store::save) } }
                mutable.value = mutable.value.copy(attempt = restored, savedPin = vault.saved)
            } catch (_: Exception) { error("No se pudo recuperar la prueba. Usa Nueva prueba para descartar solo la simulación.") }
            finally { mutable.value = mutable.value.copy(busy = false, savedPin = vault.saved) }
        }
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                state.value.attempt?.let {
                    if (System.currentTimeMillis() - it.createdAt >= Demo.WINDOW_MS && it.phase in setOf(Demo.Phase.AUTH_PENDING, Demo.Phase.RESULT_PENDING)) change(Demo::timeout)
                }
            }
        }
    }
    fun open(subscription: Int, line: String) { mutable.value = state.value.copy(open = true, subscription = subscription, line = line) }
    fun close() { mutable.value = state.value.copy(open = false) }
    fun error(message: String) { mutable.value = state.value.copy(error = message) }
    private fun persist(operation: suspend () -> Demo.Attempt?) {
        if (state.value.busy) return
        mutable.value = state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { operation() }
                mutable.value = state.value.copy(attempt = result)
            } catch (e: Exception) { error(e.message ?: "No se pudo guardar la prueba. No se ha enviado ninguna solicitud real.") }
            finally { mutable.value = state.value.copy(busy = false) }
        }
    }
    fun prepare(bank: Demo.Bank, phone: String, cup: String) {
        val current = state.value
        if (current.attempt != null) return
        persist { Demo.prepare(current.subscription, current.line, bank, phone, cup, System.currentTimeMillis()).also(store::save) }
    }
    private fun change(operation: (Demo.Attempt) -> Demo.Attempt) {
        val attempt = state.value.attempt ?: return
        persist { operation(attempt).also(store::save) }
    }
    fun authenticate(pin: String) = change { Demo.authenticate(it, pin) }
    fun message(kind: Demo.Kind) = change { Demo.receive(it, Demo.fixture(it, kind, System.currentTimeMillis())) }
    fun timeout() = change(Demo::timeout)
    fun reset() = persist { store.clear(); null }
    fun pinOperation(save: Boolean, attemptId: String) {
        if (!state.value.open || state.value.attempt?.id != attemptId || state.value.busy) return
        mutable.value = state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            var authorize = false
            try {
                val valid = withContext(Dispatchers.IO) { if (save) { vault.saveDemo(); true } else vault.unlockDemo() }
                mutable.value = state.value.copy(savedPin = vault.saved)
                authorize = !save && valid
                if (!valid) error("El PIN ficticio guardado no es válido. Olvídalo y vuelve a guardarlo.")
            } catch (_: Exception) {
                error("Android no permitió acceder al PIN ficticio. Desbloquea el dispositivo o usa Olvidar PIN y vuelve a guardarlo.")
            } finally { mutable.value = state.value.copy(busy = false) }
            if (authorize && state.value.open && state.value.attempt?.id == attemptId) authenticate(Demo.DEMO_PIN)
        }
    }
    fun forgetPin() {
        if (state.value.busy) return
        mutable.value = state.value.copy(busy = true)
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { vault.forget() }; mutable.value = state.value.copy(savedPin = false) }
            catch (_: Exception) { error("No se pudo borrar el PIN ficticio.") }
            finally { mutable.value = state.value.copy(busy = false) }
        }
    }
}
