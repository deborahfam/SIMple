package com.simple.mobile.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.simple.mobile.domain.carrier.CubacelQuery
import com.simple.mobile.domain.model.CarrierReading
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** SQLite holds only random row IDs, IVs and authenticated encrypted typed payloads. */
class EncryptedReadingStore(context: Context, name: String = "readings.db") : SQLiteOpenHelper(context, name, null, 1) {
    data class Entry(val subscription: Int, val reading: CarrierReading)
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE readings (id TEXT PRIMARY KEY NOT NULL, iv BLOB NOT NULL, payload BLOB NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) { error("Unsupported database migration") }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("simple.readings.v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("simple.readings.v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun save(subscription: Int, reading: CarrierReading, eventId: String? = null) {
        val id = eventId?.let { UUID.nameUUIDFromBytes(it.toByteArray()).toString() } ?: UUID.randomUUID().toString()
        val json = JSONObject().put("subscription", subscription).put("query", reading.query().name)
            .put("at", reading.receivedAt()).put("balance", reading.balanceCup()).put("voice", reading.voiceSeconds() as Any?)
            .put("sms", reading.smsCount() as Any?).put("expiry", reading.expiresAt() as Any?)
            .put("rechargeReceived", reading.receivedRechargeCup()).put("rechargeAvailable", reading.availableRechargeCup())
            .put("parser", "cubacel-es-v2").put("source", reading.source()).put("closes", reading.lineClosesAt() as Any?).put("nextRecharge", reading.nextRechargeAt() as Any?)
        reading.purchase()?.let { purchase ->
            json.put("purchase", JSONObject().put("bytes", purchase.totalBytes() as Any?).put("lte", purchase.lteBytes() as Any?).put("voice", purchase.voiceSeconds() as Any?)
                .put("sms", purchase.smsCount() as Any?).put("days", purchase.validityDays()).put("firstUse", purchase.startsOnFirstUse()))
        }
        val bundles = JSONArray()
        reading.bundles().forEach { b -> bundles.put(JSONObject().put("kind", b.kind()).put("bytes", b.bytes() as Any?)
            .put("unlimited", b.unlimited()).put("expiry", b.expiresAt() as Any?)) }
        json.put("bundles", bundles)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()); updateAAD(id.toByteArray()) }
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        val encrypted = try { cipher.doFinal(bytes) } finally { bytes.fill(0) }
        val values = ContentValues().apply { put("id", id); put("iv", cipher.iv); put("payload", encrypted) }
        writableDatabase.beginTransaction()
        try {
            writableDatabase.insertWithOnConflict("readings", null, values, SQLiteDatabase.CONFLICT_IGNORE).also { if (it == -1L && eventId == null) error("Insert failed") }
            writableDatabase.execSQL("DELETE FROM readings WHERE rowid NOT IN (SELECT rowid FROM readings ORDER BY rowid DESC LIMIT 300)")
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }
    @Synchronized fun history(): List<Entry> {
        val rows = mutableListOf<Entry>()
        readableDatabase.rawQuery("SELECT id,iv,payload FROM readings ORDER BY rowid DESC LIMIT 300", null).use { cursor ->
            while (cursor.moveToNext()) {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, cursor.getBlob(1)))
                    updateAAD(cursor.getString(0).toByteArray())
                }
                val bytes = cipher.doFinal(cursor.getBlob(2))
                val json = try { JSONObject(String(bytes, Charsets.UTF_8)) } finally { bytes.fill(0) }
                // v1 misread clock voice and recharge-cap wording. Keep ciphertext but do not present invalid legacy values.
                if (json.optString("parser") == "cubacel-es-v1" && json.optString("source", "USSD") == "USSD" &&
                    json.getString("query") in setOf("VOICE", "RECHARGE_STATUS")) continue
                val bundles = json.getJSONArray("bundles")
                val parsed = (0 until bundles.length()).map { index ->
                    val b = bundles.getJSONObject(index)
                    CarrierReading.Bundle(b.getString("kind"), b.longOrNull("bytes"), b.getBoolean("unlimited"), b.longOrNull("expiry"))
                }
                rows += Entry(json.getInt("subscription"), CarrierReading(CubacelQuery.valueOf(json.getString("query")), json.getLong("at"),
                    json.stringOrNull("balance"), json.longOrNull("voice"), json.longOrNull("sms"), parsed,
                    json.longOrNull("expiry"), json.stringOrNull("rechargeReceived"), json.stringOrNull("rechargeAvailable"),
                    json.optString("source", "USSD"), json.optJSONObject("purchase")?.let { p -> CarrierReading.Purchase(p.longOrNull("bytes"), p.longOrNull("lte"), p.longOrNull("voice"), p.longOrNull("sms"), p.getInt("days"), p.getBoolean("firstUse")) },
                    json.longOrNull("closes"), json.longOrNull("nextRecharge")))
            }
        }
        return rows.sortedByDescending { it.reading.receivedAt() }
    }
    @Synchronized fun clear() { writableDatabase.delete("readings", null, null) }
    private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
    private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
}
