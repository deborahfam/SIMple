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
    val reminders by lazy { com.simple.mobile.data.reminders.LocalReminders(this) }
    private val ussd by lazy { UssdDispatcher(this, worker) }
    fun dispatcher() = ussd
    override fun onCreate() {
        super.onCreate()
        worker.execute { reconcileReminders() }
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
