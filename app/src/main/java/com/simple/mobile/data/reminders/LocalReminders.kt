package com.simple.mobile.data.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import com.simple.mobile.MainActivity
import com.simple.mobile.R
import com.simple.mobile.SimpleApplication
import com.simple.mobile.data.SimRepository
import com.simple.mobile.data.local.EncryptedReadingStore.Entry
import java.security.MessageDigest

/** One inexact alarm; its intent contains no balance or raw carrier message. */
class LocalReminders(private val app: SimpleApplication) {
    private val prefs = app.getSharedPreferences("reminders", Context.MODE_PRIVATE)
    private val notifications = app.getSystemService(NotificationManager::class.java)
    private val alarms = app.getSystemService(AlarmManager::class.java)
    val enabled: Boolean get() = prefs.getBoolean("enabled", false)

    fun allowed(): Boolean = notifications.areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        notifications.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE

    @Synchronized fun setEnabled(value: Boolean) {
        val edit = prefs.edit().putBoolean("enabled", value)
        if (value && !enabled) edit.putLong("enabled_at", System.currentTimeMillis())
        edit.apply()
        if (!value) { alarms.cancel(alarmIntent()); notifications.cancelAll() }
    }

    /** Called on the app's serial storage executor, including SMS imports and background alarms. */
    @Synchronized fun reconcile(entries: List<Entry>) {
        alarms.cancel(alarmIntent())
        if (!enabled) { notifications.cancelAll(); return }
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Recordatorios de tu línea", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Vencimientos, fechas de recarga y recursos bajos según tus lecturas guardadas"
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        })
        if (!allowed()) return
        val now = System.currentTimeMillis()
        val candidates = ReminderPolicy.events(entries, prefs.getLong("enabled_at", now), app.store.rechargeDates()).map { it.copy(key = digest(it.key)) }
        val previouslySent = prefs.getStringSet("delivered", emptySet()).orEmpty().toSet()
        val active = if (app.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED)
            SimRepository(app).activeSubscriptions().filter {
                app.getSystemService(TelephonyManager::class.java)?.createForSubscriptionId(it.subscriptionId)?.simOperator == "36801"
            }.associate { it.subscriptionId to it.simSlotIndex + 1 } else emptyMap()
        val plan = ReminderPolicy.plan(candidates, previouslySent, active.keys, now)
        val sent = plan.retained.toMutableSet()
        plan.cancel.forEach { notifications.cancel(it, 0) }
        for (event in plan.due) {
            val tag = event.key
            val open = PendingIntent.getActivity(app, event.subscription,
                Intent(app, MainActivity::class.java).putExtra("reminder_subscription", event.subscription)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val title = "SIM ${active[event.subscription]} · ${event.title}"
            val publicVersion = Notification.Builder(app, CHANNEL).setSmallIcon(R.drawable.ic_sim)
                .setContentTitle("SIMple").setContentText("Tienes un recordatorio de tu línea").build()
            val notification = Notification.Builder(app, CHANNEL).setSmallIcon(R.drawable.ic_sim)
                .setContentTitle(title).setContentText(event.text).setStyle(Notification.BigTextStyle().bigText(event.text))
                .setContentIntent(open).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion).setTimeoutAfter(event.until - now).build()
            notifications.notify(tag, 0, notification)
            sent += tag
        }
        prefs.edit().putStringSet("delivered", sent).apply()
        plan.next?.let { next ->
            // Android can defer these reminders; no exact-alarm permission or background USSD.
            alarms.setWindow(AlarmManager.RTC_WAKEUP, next, 15 * 60_000L, alarmIntent())
        }
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(app, 0,
        Intent(app, ReminderReceiver::class.java).setAction(ACTION), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        const val CHANNEL = "line_reminders"
        const val ACTION = "com.simple.mobile.REMIND"
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(LocalReminders.ACTION, Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) return
        val app = context.applicationContext as SimpleApplication
        val result = goAsync()
        app.worker.execute {
            try { app.reconcileReminders() } finally { result.finish() }
        }
    }
}
