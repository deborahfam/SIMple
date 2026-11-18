package com.simple.mobile.data.local

import com.simple.mobile.domain.transfer.*
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
class EncryptedReadingStore(context: Context, name: String = "readings.db") : SQLiteOpenHelper(context, name, null, 4) {
    @Volatile var unreadableCount: Int = 0
        private set
    data class Entry(val subscription: Int, val reading: CarrierReading)
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE readings (id TEXT PRIMARY KEY NOT NULL, iv BLOB NOT NULL, payload BLOB NOT NULL, damaged INTEGER NOT NULL DEFAULT 0)")
        createProfiles(db)
        createTransfers(db)
    }
    private fun createProfiles(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE line_profiles (id TEXT PRIMARY KEY NOT NULL, iv BLOB NOT NULL, payload BLOB NOT NULL)")
    }
    private fun createTransfers(db: SQLiteDatabase) {
        for (table in listOf("recipients", "transfers"))
            db.execSQL("CREATE TABLE $table (id TEXT PRIMARY KEY NOT NULL, iv BLOB NOT NULL, payload BLOB NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        if (old < 4) createTransfers(db)
        if (old < 2) createProfiles(db)
        if (old < 3) db.execSQL("ALTER TABLE readings ADD COLUMN damaged INTEGER NOT NULL DEFAULT 0")
    }
    private fun decrypt(id: String, iv: ByteArray, payload: ByteArray): JSONObject {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)); updateAAD(id.toByteArray())
        }
        val bytes = cipher.doFinal(payload)
        return try { JSONObject(String(bytes, Charsets.UTF_8)) } finally { bytes.fill(0) }
    }
    private fun profileRows(): List<Pair<String, JSONObject>> = buildList {
        readableDatabase.rawQuery("SELECT id,iv,payload FROM line_profiles", null).use { cursor ->
            while (cursor.moveToNext()) add(cursor.getString(0) to decrypt(cursor.getString(0), cursor.getBlob(1), cursor.getBlob(2)))
        }
    }
    @Synchronized fun linePhones(): Map<Int, String> = profileRows().mapNotNull { (_, json) ->
        json.optString("phone").takeIf { it.isNotBlank() }?.let { json.getInt("subscription") to it }
    }.toMap()
    @Synchronized fun saveLineProfile(subscription: Int, nickname: String, phone: String) {
        require(subscription >= 0 && nickname.length <= 40 && nickname.none { it.isISOControl() })
        require(phone.isEmpty() || Regex("[56][0-9]{7}").matches(phone))
        updateProfile(subscription) { json ->
            json.put("nickname", nickname.trim())
            if (phone.isEmpty()) json.remove("phone") else json.put("phone", phone)
        }
    }
    @Synchronized fun nicknames(): Map<Int, String> = profileRows().associate { it.second.getInt("subscription") to it.second.getString("nickname") }
    @Synchronized fun rechargeDates(): Map<Int, java.time.LocalDate> = profileRows().mapNotNull { (_, json) ->
        json.optString("rechargeDate").takeIf { it.isNotBlank() }?.let { json.getInt("subscription") to java.time.LocalDate.parse(it) }
    }.toMap()
    @Synchronized fun setRechargeDate(subscription: Int, date: java.time.LocalDate?) {
        require(subscription >= 0)
        updateProfile(subscription) { json -> if (date == null) json.remove("rechargeDate") else json.put("rechargeDate", date.toString()) }
    }
    @Synchronized fun rename(subscription: Int, nickname: String) {
        require(subscription >= 0)
        val name = nickname.trim()
        require(name.length <= 40 && name.none { it.isISOControl() })
        updateProfile(subscription) { it.put("nickname", name) }
    }
    private fun updateProfile(subscription: Int, update: (JSONObject) -> Unit) {
        val existing = profileRows().firstOrNull { it.second.getInt("subscription") == subscription }
        val json = existing?.second ?: JSONObject().put("subscription", subscription).put("nickname", "")
        update(json)
        val id = existing?.first ?: UUID.randomUUID().toString()
        if (json.optString("nickname").isEmpty() && !json.has("rechargeDate") && !json.has("phone")) {
            writableDatabase.delete("line_profiles", "id=?", arrayOf(id))
        } else secureWrite("line_profiles", id, json)
    }
    private fun secureRows(table: String): List<Pair<String, JSONObject>> = buildList {
        readableDatabase.rawQuery("SELECT id,iv,payload FROM $table", null).use { cursor ->
            while (cursor.moveToNext()) add(cursor.getString(0) to decrypt(cursor.getString(0), cursor.getBlob(1), cursor.getBlob(2)))
        }
    }
    private fun secureWrite(table: String, id: String, json: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()); updateAAD(id.toByteArray()) }
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        val encrypted = try { cipher.doFinal(bytes) } finally { bytes.fill(0) }
        val values = ContentValues().apply { put("id", id); put("iv", cipher.iv); put("payload", encrypted) }
        check(writableDatabase.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L)
    }
    @Synchronized fun recipients(): List<Recipient> = secureRows("recipients").map { (id, j) ->
        Recipient(id, j.getString("name"), j.getString("phone"), j.getBoolean("favorite"))
    }.sortedWith(compareByDescending<Recipient> { it.favorite }.thenBy { it.name.lowercase() })
    @Synchronized fun saveRecipient(id: String?, name: String, phone: String, favorite: Boolean) {
        val validName = requireNotNull(TransferPolicy.name(name))
        val validPhone = requireNotNull(TransferPolicy.phone(phone))
        val saved = recipients()
        require(id == null || saved.any { it.id == id })
        require(saved.none { it.phone == validPhone && it.id != id }) { "Ya tienes un destinatario con ese número." }
        require(id != null || saved.size < 50) { "Puedes guardar hasta 50 destinatarios." }
        secureWrite("recipients", id ?: UUID.randomUUID().toString(), JSONObject().put("name", validName).put("phone", validPhone).put("favorite", favorite))
    }
    @Synchronized fun deleteRecipient(id: String) { writableDatabase.delete("recipients", "id=?", arrayOf(id)) }
    @Synchronized fun transfers(): List<TransferRecord> = secureRows("transfers").map { (id, j) ->
        TransferRecord(id, j.getInt("subscription"), j.getString("line"), j.getString("name"), j.getString("phone"), j.getString("amount"), j.getLong("at"), j.getString("status"))
    }.sortedByDescending { it.at }
    @Synchronized fun prepareTransfer(draft: TransferDraft): String {
        require(draft.subscription >= 0)
        val id = UUID.randomUUID().toString()
        secureWrite("transfers", id, JSONObject().put("subscription", draft.subscription).put("line", draft.line)
            .put("name", draft.name.take(40)).put("phone", requireNotNull(TransferPolicy.phone(draft.phone)))
            .put("amount", requireNotNull(TransferPolicy.amount(draft.amount))).put("at", System.currentTimeMillis()).put("status", "PREPARED"))
        writableDatabase.execSQL("DELETE FROM transfers WHERE rowid NOT IN (SELECT rowid FROM transfers ORDER BY rowid DESC LIMIT 100)")
        return id
    }
    @Synchronized fun transferStatus(id: String, status: String) {
        require(status in TransferPolicy.statuses)
        val json = secureRows("transfers").firstOrNull { it.first == id }?.second ?: return
        val old = json.getString("status")
        require((old == "PREPARED" && status in setOf("OPENED", "OPEN_FAILED")) ||
            (old in setOf("OPENED", "USER_CONFIRMED", "NOT_COMPLETED") && status in setOf("USER_CONFIRMED", "NOT_COMPLETED")))
        secureWrite("transfers", id, json.put("status", status))
    }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("simple.readings.v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("simple.readings.v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun save(subscription: Int, reading: CarrierReading, eventId: String? = null): Boolean {
        val id = eventId?.let { UUID.nameUUIDFromBytes(it.toByteArray()).toString() } ?: UUID.randomUUID().toString()
        // Duplicate SMS never decrypt or rewrite history, nor trigger retention.
        if (eventId != null && readableDatabase.rawQuery("SELECT 1 FROM readings WHERE id=?", arrayOf(id)).use { it.moveToFirst() }) return false
        // Only retention needs a complete damage classification before inserting.
        val count = readableDatabase.rawQuery("SELECT COUNT(*) FROM readings WHERE damaged=0", null).use { it.moveToFirst(); it.getLong(0) }
        if (count >= 300) history()
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
            val inserted = writableDatabase.insertWithOnConflict("readings", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
            if (!inserted) {
                val duplicate = eventId != null && writableDatabase.rawQuery("SELECT 1 FROM readings WHERE id=?", arrayOf(id)).use { it.moveToFirst() }
                check(duplicate) { "Insert failed" }
            }
            writableDatabase.execSQL("DELETE FROM readings WHERE damaged=0 AND rowid NOT IN (SELECT rowid FROM readings WHERE damaged=0 ORDER BY rowid DESC LIMIT 300)")
            writableDatabase.setTransactionSuccessful()
            return inserted
        } finally { writableDatabase.endTransaction() }
    }
    @Synchronized fun history(): List<Entry> {
        val rows = mutableListOf<Entry>()
        val damaged = mutableListOf<String>()
        val recovered = mutableListOf<String>()
        val decryptionKey = key()
        readableDatabase.rawQuery("SELECT id,iv,payload,damaged FROM readings ORDER BY rowid DESC", null).use { cursor ->
            while (cursor.moveToNext()) {
                try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, decryptionKey, GCMParameterSpec(128, cursor.getBlob(1)))
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
                if (cursor.getInt(3) != 0) recovered += cursor.getString(0)
                } catch (_: javax.crypto.AEADBadTagException) { damaged += cursor.getString(0) }
                  catch (_: org.json.JSONException) { damaged += cursor.getString(0) }
                  catch (_: IllegalArgumentException) { damaged += cursor.getString(0) }
            }
        }
        writableDatabase.beginTransaction()
        try {
            damaged.forEach { writableDatabase.execSQL("UPDATE readings SET damaged=1 WHERE id=? AND damaged=0", arrayOf(it)) }
            recovered.forEach { writableDatabase.execSQL("UPDATE readings SET damaged=0 WHERE id=?", arrayOf(it)) }
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
        unreadableCount = damaged.size
        return rows.sortedByDescending { it.reading.receivedAt() }
    }
    /** Decrypt routing metadata in a transaction; never add plaintext subscription columns. */
    @Synchronized fun clear(subscription: Int? = null) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (subscription == null) {
                db.delete("readings", null, null)
                db.delete("line_profiles", null, null)
                db.delete("recipients", null, null)
                db.delete("transfers", null, null)
            } else {
                for (table in listOf("readings", "line_profiles", "transfers")) {
                    val ids = mutableListOf<String>()
                    db.rawQuery("SELECT id,iv,payload FROM $table", null).use { cursor ->
                        while (cursor.moveToNext()) {
                            val id = cursor.getString(0)
                            if (decrypt(id, cursor.getBlob(1), cursor.getBlob(2)).getInt("subscription") == subscription) ids += id
                        }
                    }
                    ids.forEach { db.delete(table, "id=?", arrayOf(it)) }
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
    private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
}
