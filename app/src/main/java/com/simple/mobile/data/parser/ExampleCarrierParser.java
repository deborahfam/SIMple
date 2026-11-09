package com.simple.mobile.data.parser;

import com.simple.mobile.domain.model.BalanceSnapshot;
import com.simple.mobile.domain.model.DataPlanBundle;
import com.simple.mobile.domain.query.CarrierResponseParser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Example grammar only. Production profiles require real, redacted carrier fixtures.
 * Decimal dot, no grouping, ISO dates, decimal MB/GB. No guesswork across locales.
 */
public final class ExampleCarrierParser implements CarrierResponseParser {
    private static final Pattern RESPONSE = Pattern.compile(
            "\\ABalance:\\s*(?<currency>[A-Z]{3})\\s*(?<money>\\d{1,9}(?:\\.\\d{1,2})?)\\s*;\\s*"
            + "Data:\\s*(?<data>\\d{1,9}(?:\\.\\d{1,3})?)\\s*(?<unit>MB|GB)\\s*;\\s*"
            + "Minutes:\\s*(?<minutes>\\d{1,9})\\s*;\\s*Expires:\\s*(?<expiry>\\d{4}-\\d{2}-\\d{2})\\z",
            Pattern.CASE_INSENSITIVE);
    private final ZoneId carrierZone;

    public ExampleCarrierParser(ZoneId carrierZone) { this.carrierZone = carrierZone; }

    @Override public BalanceSnapshot parse(UUID accountId, Instant at, CharSequence response)
            throws UnrecognizedResponse {
        if (response == null || response.length() > 4096) throw new UnrecognizedResponse();
        String normalized = Normalizer.normalize(response, Normalizer.Form.NFKC)
                .replace('\u00a0', ' ').trim();
        Matcher match = RESPONSE.matcher(normalized);
        if (!match.matches()) throw new UnrecognizedResponse();
        try {
            Currency currency = Currency.getInstance(match.group("currency").toUpperCase(java.util.Locale.ROOT));
            long multiplier = match.group("unit").equalsIgnoreCase("GB") ? 1_000_000_000L : 1_000_000L;
            long bytes = new BigDecimal(match.group("data")).multiply(BigDecimal.valueOf(multiplier))
                    .setScale(0, RoundingMode.UNNECESSARY).longValueExact();
            // The profile defines an inclusive carrier-local date, represented as an exclusive boundary.
            Instant expiry = LocalDate.parse(match.group("expiry")).plusDays(1)
                    .atStartOfDay(carrierZone).toInstant();
            return new BalanceSnapshot(UUID.randomUUID(), accountId, at,
                    new BigDecimal(match.group("money")), currency,
                    Math.multiplyExact(Long.parseLong(match.group("minutes")), 60), expiry,
                    List.of(new DataPlanBundle("example-primary", "Primary data", bytes, null, false, expiry)),
                    BalanceSnapshot.Source.USSD, "example-en-v1");
        } catch (IllegalArgumentException | ArithmeticException | DateTimeParseException exception) {
            throw new UnrecognizedResponse();
        }
    }
}
