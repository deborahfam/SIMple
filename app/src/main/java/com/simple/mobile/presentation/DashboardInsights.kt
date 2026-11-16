package com.simple.mobile.presentation

import com.simple.mobile.data.local.EncryptedReadingStore.Entry
import com.simple.mobile.domain.carrier.CubacelQuery
import com.simple.mobile.domain.model.CarrierReading
import java.math.BigDecimal

/** Derived only from stored readings for one SIM; never queries or assumes continuous usage. */
internal object DashboardInsights {
    const val DAY = 86_400_000L
    val resources = listOf(CubacelQuery.BALANCE, CubacelQuery.DATA, CubacelQuery.VOICE, CubacelQuery.SMS)
    data class Notice(val title: String, val detail: String, val query: CubacelQuery, val at: Long?, val refreshAll: Boolean = false)
    data class Trend(val query: CubacelQuery, val before: BigDecimal, val after: BigDecimal,
                     val from: Long, val to: Long, val reduction: BigDecimal?, val explanation: String?)

    private fun value(r: CarrierReading): BigDecimal? = when (r.query()) {
        CubacelQuery.BALANCE -> r.balanceCup()?.toBigDecimalOrNull()
        CubacelQuery.DATA -> r.bundles().filter { it.kind() == "general" }.singleOrNull()?.let {
            if (it.unlimited()) null else it.bytes()?.toBigDecimal()?.divide(BigDecimal(1_000_000_000))
        }
        CubacelQuery.VOICE -> r.voiceSeconds()?.toBigDecimal()?.divide(BigDecimal(60), 2, java.math.RoundingMode.DOWN)
        CubacelQuery.SMS -> r.smsCount()?.toBigDecimal()
        else -> null
    }
    private fun expiry(r: CarrierReading): Long? = if (r.query() == CubacelQuery.DATA)
        r.bundles().filter { it.kind() == "general" }.singleOrNull()?.expiresAt() ?: r.expiresAt() else r.expiresAt()
    private fun measured(r: CarrierReading) = r.purchase() == null && r.receivedRechargeCup() == null &&
        (value(r) != null || r.query() == CubacelQuery.DATA && r.bundles().any { it.kind() == "general" && it.unlimited() })
    private fun readings(entries: List<Entry>, sub: Int, now: Long) = entries
        .filter { it.subscription == sub && it.reading.receivedAt() <= now }.map { it.reading }.sortedByDescending { it.receivedAt() }
    private fun label(q: CubacelQuery) = when (q) {
        CubacelQuery.BALANCE -> "Saldo"; CubacelQuery.DATA -> "Internet general"
        CubacelQuery.VOICE -> "Minutos"; CubacelQuery.BONUSES_AND_USD_PLANS -> "Bonos"; else -> "Mensajes"
    }
    fun notices(entries: List<Entry>, sub: Int, now: Long): List<Notice> {
        val readings = readings(entries, sub, now)
        val urgent = mutableListOf<Notice>()
        val refresh = mutableListOf<Notice>()
        for (q in resources + CubacelQuery.BONUSES_AND_USD_PLANS) {
            val r = readings.firstOrNull { it.query() == q && (measured(it) || it.purchase() == null && it.bundles().isNotEmpty()) }
            if (r == null) {
                if (q == CubacelQuery.BONUSES_AND_USD_PLANS) continue
                refresh += Notice("Consulta tus recursos", "Faltan lecturas de esta línea. Actualiza para conocer tus recursos.", q, null, refreshAll = true)
                continue
            }
            val expiration = (listOfNotNull(r.expiresAt()) + r.bundles().mapNotNull { it.expiresAt() }).minOrNull()
            val amount = value(r)
            when {
                expiration != null && expiration <= now -> urgent += Notice("${label(q)}: vigencia terminada", "Una vigencia guardada terminó. Consulta para confirmar los recursos actuales.", q, r.receivedAt())
                expiration != null && expiration - now <= DAY -> urgent += Notice("${label(q)}: vence en menos de 24 h", "Según la vigencia informada por el operador. Revisa antes de comprar otro plan.", q, r.receivedAt())
                now - r.receivedAt() >= DAY -> refresh += Notice("Actualiza tus lecturas", "Hay recursos sin consultar en las últimas 24 horas.", q, r.receivedAt(), refreshAll = true)
                amount != null && amount <= when(q) { CubacelQuery.BALANCE -> BigDecimal(20); CubacelQuery.DATA -> BigDecimal("0.1"); else -> BigDecimal(5) } ->
                    urgent += Notice("${label(q)}: nivel bajo", "Tu última lectura indica pocos recursos. Consulta para confirmar el valor actual.", q, r.receivedAt())
            }
        }
        return urgent + refresh.distinctBy { it.title }
    }
    fun trends(entries: List<Entry>, sub: Int, now: Long): List<Trend> {
        val readings = readings(entries, sub, now)
        return resources.mapNotNull { q ->
            val samples = readings.filter { it.query() == q && measured(it) && now - it.receivedAt() <= 7 * DAY }
                .distinctBy { it.receivedAt() }.take(2)
            if (samples.size < 2) return@mapNotNull null
            val newest = samples[0]; val older = samples[1]
            val after = value(newest) ?: return@mapNotNull null
            val before = value(older) ?: return@mapNotNull null
            val reason = when {
                readings.any { it.receivedAt() > older.receivedAt() && it.receivedAt() <= newest.receivedAt() && (it.purchase() != null || it.receivedRechargeCup() != null) } -> "Compra o recarga entre lecturas"
                expiry(older) != expiry(newest) -> "Cambió la vigencia"
                expiry(older)?.let { it <= newest.receivedAt() } == true -> "La vigencia terminó entre lecturas"
                after > before -> "Aumento de recursos; posible renovación o recarga"
                else -> null
            }
            Trend(q, before, after, older.receivedAt(), newest.receivedAt(), if (reason == null) before - after else null, reason)
        }
    }
}
