package com.simple.mobile.data.reminders

import com.simple.mobile.data.local.EncryptedReadingStore.Entry
import com.simple.mobile.domain.carrier.CubacelQuery
import com.simple.mobile.domain.model.CarrierReading
import java.math.BigDecimal

/** Pure policy: no modem queries, inferred consumption, or inferred expiry dates. */
internal object ReminderPolicy {
    const val DAY = 86_400_000L
    data class Event(val key: String, val subscription: Int, val due: Long, val until: Long,
                     val title: String, val text: String)

    data class Plan(val due: List<Event>, val next: Long?, val retained: Set<String>, val cancel: Set<String>)
    fun plan(events: List<Event>, delivered: Set<String>, active: Set<Int>, now: Long): Plan {
        val retained = delivered.intersect(events.map { it.key }.toSet())
        val valid = events.filter { it.subscription in active && now < it.until }
        val pending = valid.filter { it.key !in retained }
        return Plan(pending.filter { it.due <= now }, pending.filter { it.due > now }.minOfOrNull { it.due },
            retained, delivered - valid.map { it.key }.toSet())
    }

    fun events(entries: List<Entry>, enabledAt: Long, rechargeDates: Map<Int, java.time.LocalDate> = emptyMap()): List<Event> {
        // Purchase/recharge receipts must not displace a measured balance.
        val latest = entries.filter { it.reading.query() != CubacelQuery.BALANCE || it.reading.balanceCup() != null }
            .groupBy { it.subscription to it.reading.query() }
            .mapValues { (_, values) -> values.maxBy { it.reading.receivedAt() }.reading }
        return buildList {
            for ((sub, day) in rechargeDates) {
                val zone = java.time.ZoneId.of("America/Havana")
                val due = day.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
                val until = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                add(Event("planned-recharge:$sub:$day", sub, due, until, "Hoy toca tu recarga",
                    "Hoy es la fecha que elegiste para esta tarjeta. Revisa tus recursos y confirma las condiciones antes de recargar."))
            }
            for ((identity, r) in latest) {
                val (sub, query) = identity
                val name = when (query) {
                    CubacelQuery.BALANCE -> "Saldo"
                    CubacelQuery.DATA -> "Datos"
                    CubacelQuery.VOICE -> "Minutos"
                    CubacelQuery.SMS -> "SMS"
                    CubacelQuery.BONUSES_AND_USD_PLANS -> "Bonos"
                    CubacelQuery.RECHARGE_STATUS -> "Recarga"
                }
                val expiries = (listOfNotNull(r.expiresAt()) + r.bundles().mapNotNull { it.expiresAt() }).distinct()
                for (expiry in expiries) add(Event("expiry:$sub:$query:$expiry", sub, expiry - DAY, expiry,
                    "$name: vencimiento próximo", "Una vigencia informada por el operador termina en menos de 24 horas. Abre SIMple para revisar la fecha."))
                if (query == CubacelQuery.RECHARGE_STATUS && sub !in rechargeDates) r.nextRechargeAt()?.let { date ->
                    add(Event("recharge:$sub:$date", sub, date, date + DAY, "Revisa tu cupo de recarga",
                        "Llegó la fecha indicada por el operador. Consulta de nuevo para confirmar tu cupo; la fecha no garantiza disponibilidad."))
                }
                if (r.receivedAt() >= enabledAt && low(r)) {
                    // A stable key suppresses repeated low readings until a newer reading recovers.
                    val quotaExpiry = if (query == CubacelQuery.DATA)
                        r.bundles().firstOrNull { it.kind() == "general" }?.expiresAt() ?: r.expiresAt() else r.expiresAt()
                    add(Event("low:$sub:$query", sub, r.receivedAt(), minOf(r.receivedAt() + DAY, quotaExpiry ?: Long.MAX_VALUE),
                        "$name: nivel bajo", "Tu última lectura indica pocos recursos. Abre SIMple para comprobar los valores y cuándo se actualizaron."))
                }
            }
        }
    }

    private fun low(r: CarrierReading): Boolean = when (r.query()) {
        CubacelQuery.BALANCE -> r.balanceCup()?.let { BigDecimal(it) <= BigDecimal(20) } ?: false
        CubacelQuery.DATA -> r.bundles().firstOrNull { it.kind() == "general" }?.let {
            !it.unlimited() && it.bytes()?.let { bytes -> bytes <= 100_000_000L } == true
        } ?: false
        CubacelQuery.VOICE -> r.voiceSeconds()?.let { it <= 300 } ?: false
        CubacelQuery.SMS -> r.smsCount()?.let { it <= 5 } ?: false
        else -> false
    }
}
