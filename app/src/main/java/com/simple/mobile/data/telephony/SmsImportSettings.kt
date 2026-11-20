package com.simple.mobile.data.telephony

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Import consent and status are shared by the UI, inbox sync and receiver. No message content is stored here. */
data class SmsSyncStatus(val enabled: Boolean = false, val syncing: Boolean = false,
                         val lastAt: Long? = null, val lastAdded: Int = 0, val moreInbox: Boolean = false, val error: String? = null, val bankEnabled: Boolean = false)

class SmsImportSettings(context: Context, preferenceName: String = "features") {
    private val prefs = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(SmsSyncStatus((com.simple.mobile.BuildConfig.DIRECT_DISTRIBUTION && prefs.getBoolean("carrier_sms", false)),
        lastAt = prefs.getLong("sms_last_at", 0).takeIf { it > 0 }, lastAdded = prefs.getInt("sms_last_added", 0), bankEnabled = (com.simple.mobile.BuildConfig.DIRECT_DISTRIBUTION && prefs.getBoolean("bank_sms", false))))
    val state = mutable.asStateFlow()
    private var generation = 0L
    private var activeSync: Long? = null

    @Synchronized fun setEnabled(enabled: Boolean) {
        if (enabled && !com.simple.mobile.BuildConfig.DIRECT_DISTRIBUTION) return
        if (enabled != mutable.value.enabled) generation++
        prefs.edit().putBoolean("carrier_sms", enabled).apply()
        mutable.value = mutable.value.copy(enabled = enabled, error = null)
    }
    @Synchronized fun setBankEnabled(enabled: Boolean) {
        if (enabled && !com.simple.mobile.BuildConfig.DIRECT_DISTRIBUTION) return
        if (enabled != mutable.value.bankEnabled) generation++
        prefs.edit().putBoolean("bank_sms", enabled).remove("inbox_id").remove("inbox_scope").apply()
        mutable.value = mutable.value.copy(bankEnabled = enabled, error = null)
    }
    @Synchronized fun token(): Long? = if (mutable.value.enabled) generation else null
    @Synchronized fun isCurrent(token: Long): Boolean = mutable.value.enabled && token == generation
    @Synchronized fun saveIfEnabled(token: Long, save: () -> Boolean): Boolean = isCurrent(token) && save()
    @Synchronized fun beginSync(): Long? {
        if (!mutable.value.enabled || activeSync != null) return null
        activeSync = generation
        mutable.value = mutable.value.copy(syncing = true, error = null)
        return generation
    }
    @Synchronized fun finishSync(token: Long, count: Int, error: String? = null) {
        if (activeSync != token) return
        activeSync = null
        mutable.value = mutable.value.copy(syncing = false)
        if (isCurrent(token)) {
            if (error == null) record(count) else mutable.value = mutable.value.copy(error = error)
        }
    }
    @Synchronized fun received(token: Long, count: Int) {
        if (isCurrent(token) && count > 0) record(count)
    }
    @Synchronized fun inboxCursor(scope: String): Long? =
        prefs.getLong("inbox_id", -1).takeIf { it >= 0 && prefs.getString("inbox_scope", null) == scope }
    @Synchronized fun checkpoint(token: Long, scope: String, id: Long, more: Boolean) {
        if (!isCurrent(token)) return
        prefs.edit().putString("inbox_scope", scope).putLong("inbox_id", id).apply()
        mutable.value = mutable.value.copy(moreInbox = more)
    }
    @Synchronized fun clearSummary() {
        prefs.edit().remove("sms_last_at").remove("sms_last_added").remove("inbox_id").remove("inbox_scope").apply()
        mutable.value = mutable.value.copy(lastAt = null, lastAdded = 0, moreInbox = false, error = null)
    }
    private fun record(count: Int) {
        val at = System.currentTimeMillis()
        prefs.edit().putLong("sms_last_at", at).putInt("sms_last_added", count).apply()
        mutable.value = mutable.value.copy(lastAt = at, lastAdded = count, error = null)
    }
}
