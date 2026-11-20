package com.simple.mobile

import android.app.Application
import com.simple.mobile.data.local.EncryptedReadingStore
import com.simple.mobile.data.telephony.UssdDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.Executors

class SimpleApplication : Application() {
    val worker = Executors.newSingleThreadExecutor()
    val store by lazy { EncryptedReadingStore(this) }
    val readings = MutableStateFlow<List<EncryptedReadingStore.Entry>>(emptyList())
    val bankNoticeStore by lazy { com.simple.mobile.data.recharge.BankRechargeNoticeStore(this) }
    val bankNotices = MutableStateFlow<List<com.simple.mobile.data.recharge.BankRechargeNotice>>(emptyList())
    val bankNoticeError = MutableStateFlow(false)
    fun refreshBankNotices() {
        try { bankNotices.value = bankNoticeStore.history(); bankNoticeError.value = false }
        catch (_: Exception) { bankNotices.value = emptyList(); bankNoticeError.value = true }
    }
    val smsImport by lazy { com.simple.mobile.data.telephony.SmsImportSettings(this) }
    val reminders by lazy { com.simple.mobile.data.reminders.LocalReminders(this) }
    private val ussd by lazy { UssdDispatcher(this, worker) }
    fun dispatcher() = ussd
    val purchases by lazy {
        com.simple.mobile.domain.plans.PlanPurchaseController(com.simple.mobile.data.plans.PlanPurchaseStore(this), worker) { sub, id ->
            val result = java.util.concurrent.CompletableFuture<String>()
            ussd.purchasePlan(sub, id).whenComplete { reply, error ->
                val failure = (error as? UssdDispatcher.QueryException)?.failure
                if (error == null) result.complete(reply)
                else if (failure in setOf(UssdDispatcher.Failure.PERMISSION, UssdDispatcher.Failure.INACTIVE_SIM,
                        UssdDispatcher.Failure.WRONG_CARRIER, UssdDispatcher.Failure.BUSY))
                    result.completeExceptionally(com.simple.mobile.domain.plans.PlanNotSentException())
                else result.completeExceptionally(error)
            }
            result
        }
    }
    val bankRecharges by lazy {
        com.simple.mobile.domain.recharge.BankRechargeController(
            com.simple.mobile.data.recharge.BankRechargeStore(this), worker,
            { sub, pin -> bankResult(ussd.authenticateMetropolitano(sub, pin)) },
            { sub, phone, cup -> bankResult(ussd.rechargeMetropolitano(sub, phone, cup)) })
    }
    private fun bankResult(source: java.util.concurrent.CompletableFuture<String>): java.util.concurrent.CompletableFuture<String> {
        val result = java.util.concurrent.CompletableFuture<String>()
        source.whenComplete { reply, error ->
            val failure = (error as? UssdDispatcher.QueryException)?.failure
            if (error == null) result.complete(reply)
            else if (failure in setOf(UssdDispatcher.Failure.PERMISSION, UssdDispatcher.Failure.INACTIVE_SIM,
                    UssdDispatcher.Failure.WRONG_CARRIER, UssdDispatcher.Failure.BUSY))
                result.completeExceptionally(com.simple.mobile.domain.recharge.BankNotSentException())
            else result.completeExceptionally(error)
        }
        return result
    }
    override fun onCreate() {
        super.onCreate()
        worker.execute {
            try { com.simple.mobile.data.recharge.BankPinVault.removeLegacy(this) }
            catch (_: Exception) { /* Vault access also retries cleanup and fails closed. */ }
            reconcileReminders(); refreshBankNotices()
        }
    }
    fun refreshReadings() { publishReadings(store.history()) }
    fun publishReadings(entries: List<EncryptedReadingStore.Entry>) {
        readings.value = entries
        reconcileReminders(entries)
    }
    fun reconcileReminders(entries: List<EncryptedReadingStore.Entry>? = null) {
        try { reminders.reconcile(entries ?: if (reminders.enabled) store.history() else emptyList()) }
        catch (_: Exception) { /* Reminder failures must never interrupt saving carrier readings. Retry on next app open/update. */ }
    }
}
