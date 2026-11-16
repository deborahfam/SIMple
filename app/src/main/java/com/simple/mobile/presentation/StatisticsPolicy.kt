package com.simple.mobile.presentation

import com.simple.mobile.data.local.EncryptedReadingStore.Entry
import com.simple.mobile.domain.carrier.CubacelQuery
import com.simple.mobile.domain.model.CarrierReading
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

internal object StatisticsPolicy {
    val zone: ZoneId = ZoneId.of("America/Havana")
    fun date(at: Long): LocalDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    fun recharge(entries: List<Entry>, sub: Int, now: Long): LocalDate? = entries
        .filter { it.subscription == sub && it.reading.query() == CubacelQuery.RECHARGE_STATUS && it.reading.receivedAt() <= now }
        .maxByOrNull { it.reading.receivedAt() }?.reading?.nextRechargeAt()?.let(::date)
    data class Budget(val dailyBytes: Long? = null, val days: Long = 0, val remaining: Long? = null, val message: String)
    fun budget(entries: List<Entry>, sub: Int, target: LocalDate?, now: Long): Budget {
        if (target == null) return Budget(message = "Elige tu próxima recarga para calcular un límite diario.")
        val days = ChronoUnit.DAYS.between(date(now), target)
        if (days <= 0) return Budget(message = if (days == 0L) "Hoy es tu fecha de recarga. Revisa tus recursos antes de comprar." else "La fecha objetivo pasó. Elige tu próxima recarga.")
        val reading = entries.filter { it.subscription == sub && it.reading.query() == CubacelQuery.DATA && it.reading.receivedAt() <= now && it.reading.purchase() == null }
            .maxByOrNull { it.reading.receivedAt() }?.reading ?: return Budget(message = "Consulta tus datos para calcular el presupuesto.")
        if (now - reading.receivedAt() >= DashboardInsights.DAY) return Budget(message = "Actualiza los datos: la lectura tiene más de 24 horas.")
        val bundle = reading.bundles().filter { it.kind() == "general" }.singleOrNull()
            ?: return Budget(message = "No hay una cantidad única de Internet general para repartir.")
        if (bundle.unlimited()) return Budget(message = "Internet general ilimitado: no hace falta repartir una cuota.")
        val bytes = bundle.bytes() ?: return Budget(message = "La cantidad disponible es desconocida.")
        val expiry = bundle.expiresAt() ?: reading.expiresAt()
        if (expiry != null && expiry <= now) return Budget(message = "El paquete figura vencido. Consulta tus datos de nuevo.")
        if (expiry != null && expiry < target.atStartOfDay(zone).toInstant().toEpochMilli())
            return Budget(message = "El paquete vence el ${date(expiry)} antes de tu próxima recarga. No puede cubrir todo el período.")
        return Budget(bytes / days, days, bytes, "Reparte la última cuota entre $days días, incluido hoy. " +
            if (expiry == null) "Vigencia desconocida: confirma que el paquete llegue a esa fecha." else "Según la vigencia guardada.")
    }
    data class Reduction(val query: CubacelQuery, val amount: BigDecimal?, val intervals: Int, val omitted: Int, val from: Long?, val to: Long?)
    private fun amount(r: CarrierReading): BigDecimal? = when (r.query()) {
        CubacelQuery.BALANCE -> r.balanceCup()?.toBigDecimalOrNull()
        CubacelQuery.DATA -> r.bundles().filter { it.kind() == "general" }.singleOrNull()?.takeUnless { it.unlimited() }?.bytes()?.toBigDecimal()
        CubacelQuery.VOICE -> r.voiceSeconds()?.toBigDecimal()
        CubacelQuery.SMS -> r.smsCount()?.toBigDecimal()
        else -> null
    }
    private fun expiry(r: CarrierReading) = if (r.query() == CubacelQuery.DATA)
        r.bundles().filter { it.kind() == "general" }.singleOrNull()?.expiresAt() ?: r.expiresAt() else r.expiresAt()
    fun reductions(entries: List<Entry>, sub: Int, days: Int, now: Long): List<Reduction> {
        val readings = entries.filter { it.subscription == sub && it.reading.receivedAt() in (now - days * DashboardInsights.DAY)..now }
            .map { it.reading }.sortedBy { it.receivedAt() }
        return DashboardInsights.resources.map { q ->
            val samples = readings.filter { it.query() == q && it.purchase() == null && it.receivedRechargeCup() == null }
                .distinctBy { it.receivedAt() }
            var total = BigDecimal.ZERO; var intervals = 0; var omitted = 0
            var first: Long? = null; var last: Long? = null
            for ((before, after) in samples.zipWithNext()) {
                val a = amount(before); val b = amount(after)
                val intervening = readings.any { it.receivedAt() > before.receivedAt() && it.receivedAt() <= after.receivedAt() && (it.purchase() != null || it.receivedRechargeCup() != null) }
                if (a == null || b == null || b > a || intervening || expiry(before) != expiry(after) || expiry(before)?.let { it <= after.receivedAt() } == true) { omitted++; continue }
                total += a - b; intervals++
                if (first == null) first = before.receivedAt()
                last = after.receivedAt()
            }
            Reduction(q, total.takeIf { intervals > 0 }, intervals, omitted, first, last)
        }
    }
    fun bytes(value: Long): String = if (value >= 1_000_000_000) "${BigDecimal(value).divide(BigDecimal(1_000_000_000), 2, RoundingMode.DOWN)} GB"
        else "${BigDecimal(value).divide(BigDecimal(1_000_000), 1, RoundingMode.DOWN)} MB"
}
