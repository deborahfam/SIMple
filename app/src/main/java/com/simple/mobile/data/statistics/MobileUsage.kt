package com.simple.mobile.data.statistics

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Process

data class AppTraffic(val uid: Int, val name: String, val bytes: Long)
data class MobileUsageState(val loading: Boolean = false, val allowed: Boolean = false, val apps: List<AppTraffic> = emptyList(), val error: String? = null, val days: Int = 7)

class MobileUsage(private val context: Context) {
    @Suppress("DEPRECATION")
    fun allowed(): Boolean = context.getSystemService(AppOpsManager::class.java)
        .checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED

    @Suppress("DEPRECATION")
    fun read(days: Int, now: Long): MobileUsageState {
        if (!allowed()) return MobileUsageState(days = days)
        return try {
            val totals = mutableMapOf<Int, Long>()
            // null means ALL mobile subscriptions, never the selected SIM. No IMSI is read.
            val stats = context.getSystemService(NetworkStatsManager::class.java)
                .querySummary(ConnectivityManager.TYPE_MOBILE, null, now - days * 86_400_000L, now)
                ?: return MobileUsageState(allowed = true, days = days, error = "Android no devolvió estadísticas de tráfico.")
            try {
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    totals[bucket.uid] = (totals[bucket.uid] ?: 0L) + bucket.rxBytes + bucket.txBytes
                }
            } finally { stats.close() }
            val pm = context.packageManager
            val apps = totals.filterValues { it > 0 }.map { (uid, bytes) ->
                val names = pm.getPackagesForUid(uid).orEmpty().mapNotNull { name ->
                    try { pm.getApplicationLabel(pm.getApplicationInfo(name, 0)).toString() } catch (_: Exception) { null }
                }.distinct()
                AppTraffic(uid, names.joinToString(" / ").ifBlank { if (uid < 0) "Sistema y tráfico no atribuido" else "Aplicación o servicio · UID $uid" }, bytes)
            }.sortedByDescending { it.bytes }
            MobileUsageState(allowed = true, apps = apps, days = days)
        } catch (_: SecurityException) {
            MobileUsageState(allowed = allowed(), days = days, error = "Android no permite leer el consumo. Revisa el acceso de uso.")
        } catch (_: Exception) {
            MobileUsageState(allowed = true, days = days, error = "No se pudo leer el consumo. Inténtalo de nuevo.")
        }
    }
}
