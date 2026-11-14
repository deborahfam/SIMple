package com.simple.mobile.data.telephony

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.simple.mobile.SimpleApplication
import com.simple.mobile.data.SimRepository
import com.simple.mobile.data.parser.CubacelParser

/** No raw message is logged or retained. Only a fixed carrier sender list is queried. */
object CarrierSmsSync {
    private val senders = setOf("etecsa", "cubacel", "2266", "222")
    private fun cubacel(context: Context, id: Int): Boolean =
        context.getSystemService(TelephonyManager::class.java)?.createForSubscriptionId(id)?.simOperator == "36801"
    fun ingest(app: SimpleApplication, sender: String?, body: String, subscription: Int, at: Long, event: String, token: Long): Int {
        if (!app.smsImport.isCurrent(token)) return 0
        if (sender?.lowercase(java.util.Locale.ROOT) !in senders || subscription < 0 || !cubacel(app, subscription)) return 0
        val parsed = try { CubacelParser().parseSms(body, at) } catch (_: CubacelParser.ParseException) { return 0 }
        return parsed.count { reading -> app.smsImport.saveIfEnabled(token) {
            app.store.save(subscription, reading, "$event:${reading.query()}")
        } }
    }
    fun eventId(subscription: Int, at: Long, body: String): String {
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(body.toByteArray()).joinToString("") { "%02x".format(it) }
        return "message:$subscription:$at:$hash"
    }
    fun syncInbox(app: SimpleApplication, token: Long): Int {
        if (app.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) throw SecurityException()
        val valid = SimRepository(app).activeSubscriptions().filter { cubacel(app, it.subscriptionId) }.map { it.subscriptionId }.toSet()
        if (valid.isEmpty()) return 0
        var count = 0
        // Selection is applied by the provider, before bodies are read into the app process.
        app.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI, arrayOf("_id", "address", "body", "date", "sub_id", "date_sent"),
            "LOWER(address) IN (?,?,?,?)", senders.toTypedArray(), "date DESC").let { cursor ->
            checkNotNull(cursor) { "SMS provider unavailable" }
            cursor.use {
            var seen = 0
            while (app.smsImport.isCurrent(token) && cursor.moveToNext() && seen++ < 150) {
                val sub = cursor.getInt(4)
                // Do not silently attribute historical messages with an unknown SIM to a current line.
                if (sub !in valid) continue
                val body = cursor.getString(2) ?: continue
                val at = cursor.getLong(5).takeIf { it > 0 } ?: cursor.getLong(3)
                count += ingest(app, cursor.getString(1), body, sub, at, eventId(sub, at, body), token)
            }
            }
        }
        return count
    }
}

class CarrierSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val app = context.applicationContext as SimpleApplication
        val token = app.smsImport.token() ?: return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return
        val subscription = if (android.os.Build.VERSION.SDK_INT >= 29)
            intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, intent.getIntExtra("subscription", -1))
            else intent.getIntExtra("subscription", -1)
        val pending = goAsync()
        app.worker.execute {
            try {
                val body = messages.joinToString("") { it.messageBody.orEmpty() }
                val count = CarrierSmsSync.ingest(app, messages.first().originatingAddress, body, subscription,
                    messages.first().timestampMillis, CarrierSmsSync.eventId(subscription, messages.first().timestampMillis, body), token)
                app.smsImport.received(token, count)
                app.refreshReadings()
            } catch (_: Exception) { /* No sensitive message or exception logging. */ }
            finally { pending.finish() }
        }
    }
}
