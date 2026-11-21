package com.simple.mobile.migrationprobe;

import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import org.json.JSONObject;
import java.security.KeyStore;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Standalone platform-only instrumentation for the actual R8-optimized release. */
public final class AfterUpgradeProbe extends Instrumentation {
    private Bundle args;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); args = arguments; start(); }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            require(android.os.Build.MODEL.startsWith("sdk_gphone"), "Emulator only");
            Context context = getTargetContext();
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            require(info.getLongVersionCode() == 23L, "Unexpected version");
            require((context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0, "Release must not be debuggable");
            require(info.signingInfo.getApkContentsSigners().length == 1, "Expected one current signer");
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(info.signingInfo.getApkContentsSigners()[0].toByteArray());
            StringBuilder digest = new StringBuilder();
            for (byte b : hash) digest.append(String.format("%02x", b));
            require(digest.toString().equals(args.getString("productionDigest")), "Wrong current signing key");
            require(info.signingInfo.getSigningCertificateHistory().length == 2, "Missing rotation history");
            android.content.SharedPreferences prefs = context.getSharedPreferences("migration_probe", Context.MODE_PRIVATE);
            require("synthetic-v22".equals(prefs.getString("marker", null)), "Lost marker");
            require(prefs.getInt("uid", -1) == android.os.Process.myUid(), "App UID changed");
            KeyStore keys = KeyStore.getInstance("AndroidKeyStore"); keys.load(null);
            SecretKey key = (SecretKey) keys.getKey("simple.readings.v1", null);
            require(key != null, "Lost encryption key");
            String[][] records = {{"readings", "balance", "12.34"}, {"line_profiles", "nickname", "Synthetic migration"}, {"recipients", "name", "Synthetic recipient"}};
            try (SQLiteDatabase db = SQLiteDatabase.openDatabase(context.getDatabasePath("migration-probe.db").getPath(), null, SQLiteDatabase.OPEN_READONLY)) {
                for (String[] record : records) {
                    try (Cursor cursor = db.rawQuery("SELECT id,iv,payload FROM " + record[0], null)) {
                        require(cursor.moveToFirst(), "Missing encrypted record");
                        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, cursor.getBlob(1)));
                        cipher.updateAAD(cursor.getString(0).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        byte[] plain = cipher.doFinal(cursor.getBlob(2));
                        try { require(record[2].equals(new JSONObject(new String(plain, java.nio.charset.StandardCharsets.UTF_8)).getString(record[1])), "Data mismatch"); }
                        finally { java.util.Arrays.fill(plain, (byte) 0); }
                    }
                }
            }
            android.content.SharedPreferences pins = context.getSharedPreferences("bank_pins", Context.MODE_PRIVATE);
            for (int i = 0; i < 20 && pins.contains("900099.data"); i++) Thread.sleep(250);
            require(!pins.contains("900099.data"), "Legacy PIN retained");
            require(pins.getBoolean("900099.replace", false), "Missing replacement notice");
            require(!keys.containsAlias("simple.metro.pin.v2.900099"), "Legacy PIN key retained");
            result.putString("stream", "PASS: production signer, release flags, UID, preferences, encrypted history/profile/recipient, and legacy-PIN cleanup.\n");
            finish(-1, result);
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + error.getClass().getSimpleName() + ": " + error.getMessage() + "\n");
            finish(0, result);
        }
    }
}
