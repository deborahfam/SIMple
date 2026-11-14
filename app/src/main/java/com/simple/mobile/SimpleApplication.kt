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
    private val ussd by lazy { UssdDispatcher(this, worker) }
    fun dispatcher() = ussd
    fun refreshReadings() { readings.value = store.history() }
}
