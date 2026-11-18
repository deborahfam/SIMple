package com.simple.mobile.domain.recharge

import java.math.BigDecimal
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/** Classifies observed responses only. Does not authenticate a sender or settle a payment.
 * A rejection carries a destination and quota, not the requested amount or a transaction ID.
 * Correlation with a future real attempt must be performed separately, including late replies.
 */
object MetropolitanoResponseParser {
    data class QuotaRejection(val destination: String, val limitCup: BigDecimal, val afterDate: LocalDate)
    private val dateFormat = DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT)
    private val quota = Regex(
        "fallo la recarga del movil (?:\\+?53)?([56][0-9]{7}) alcanzo el monto limite de recarga permitido en 30 dias " +
            "\\(([0-9]{1,6}(?:[.,][0-9]{1,2})?) cup\\), puede recargar posterior al dia ([0-9]{2}-[0-9]{2}-[0-9]{4})\\.?"
    )
    private const val PROCESSING = "su solicitud esta siendo procesada, espere un sms con los datos solicitados y detalles de la transaccion realizada"
    private fun normalized(body: String): String? {
        if (body.length > 4096) return null
        return Normalizer.normalize(body, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")
    }
    fun isProcessingAcknowledgement(ussd: String): Boolean = normalized(ussd)?.trimEnd('.') == PROCESSING
    fun quotaRejection(sender: String?, body: String): QuotaRejection? {
        if (!sender.equals("PAGOxMOVIL", ignoreCase = true)) return null
        val match = quota.matchEntire(normalized(body) ?: return null) ?: return null
        val amount = match.groupValues[2].replace(',', '.').toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: return null
        val date = try { LocalDate.parse(match.groupValues[3], dateFormat) } catch (_: Exception) { return null }
        return QuotaRejection(match.groupValues[1], amount, date)
    }
}
