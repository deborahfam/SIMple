package com.simple.mobile.data.parser;

import com.simple.mobile.domain.carrier.CubacelQuery;
import com.simple.mobile.domain.model.CarrierReading;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;
import java.util.regex.*;

/** Bounded, command-specific Spanish grammars. Never logs or persists response text.
 * Rejects ambiguous quantities instead of treating a failed parse as zero.
 */
public final class CubacelParser {
    private static final String NUMBER = "(?<![\\d.,-])(\\d{1,9}(?:[.,]\\d{1,3})?)(?![\\d.,])";
    private static final Pattern DATA = Pattern.compile(NUMBER + "\\s*(GB|MB|KB)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MONEY = Pattern.compile(NUMBER + "\\s*(?:CUP|MN)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern VOICE = Pattern.compile(NUMBER + "\\s*(?:minutos?|min)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SMS = Pattern.compile("(?<![\\d.,-])(\\d{1,9})\\s*(?:SMS|mensajes)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPIRY = Pattern.compile("(?:vence(?:n)?|vencimiento|expira(?:cion)?|valido hasta|vigente hasta|vigencia(?: hasta)?)\\s*(?:el|:)?\\s*(\\d{1,4}[/-]\\d{1,2}[/-]\\d{2,4})\\b");
    private static final Pattern NEGATIVE = Pattern.compile("(?:error|no se pudo|no disponible|solicitud no|intente|servicio no|saldo insuficiente|comprar|oferta|promo|si recib|por solo)");
    public static final class ParseException extends Exception {
        public ParseException() { super("Unrecognized or ambiguous Cubacel response"); }
    }

    public CarrierReading parse(CubacelQuery query, CharSequence response, long receivedAt) throws ParseException {
        if (query == null || response == null || response.length() == 0 || response.length() > 4096) throw new ParseException();
        String text = Normalizer.normalize(response, Normalizer.Form.NFKD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replace('\u00a0', ' ');
        if (NEGATIVE.matcher(text).find() || text.contains("cuc")) throw new ParseException();
        String balance = null, rechargeReceived = null, rechargeAvailable = null;
        Long voice = null, sms = null;
        List<CarrierReading.Bundle> bundles = new ArrayList<>();
        Long expiry = null, closesAt = null, nextRecharge = null;
        Set<String> kinds = new HashSet<>();
        try {
            if (query == CubacelQuery.BALANCE) {
                Matcher money = MONEY.matcher(text);
                if (money.find()) {
                    String prefix = text.substring(Math.max(0, money.start() - 55), money.start());
                    if (!prefix.matches("(?s).*(?:saldo(?: principal)?|dispone de|disponible)\\s*(?:es|de)?\\s*[:=]?\\s*\\$?\\s*")) throw new ParseException();
                    balance = decimal(money.group(1)).toPlainString();
                    if (money.find()) throw new ParseException();
                }
            }
            if (query == CubacelQuery.RECHARGE_STATUS) {
                Matcher reached = Pattern.compile("ha alcanzado el monto de recarga permitido de\\s*" + NUMBER + "\\s*cup").matcher(text);
                if (reached.find()) rechargeAvailable = "0";
                Matcher received = Pattern.compile("(?:recibid[oa]s?|ha recargado|recargado|acumulado)\\s*[:=]?\\s*" + NUMBER + "\\s*cup").matcher(text);
                if (received.find()) { rechargeReceived = decimal(received.group(1)).toPlainString(); if (received.find()) throw new ParseException(); }
                Matcher available = Pattern.compile("(?:disponible(?: para recargar)?|puede recargar(?: aun| hasta)?|queda(?:n)? por recargar)\\s*[:=]?\\s*(?:de\\s+)?" + NUMBER + "\\s*cup").matcher(text);
                if (available.find()) {
                    String value = decimal(available.group(1)).toPlainString();
                    if (rechargeAvailable != null && !rechargeAvailable.equals(value)) throw new ParseException();
                    rechargeAvailable = value;
                    if (available.find()) throw new ParseException();
                }
                Matcher next = Pattern.compile("(?:puede recargar\\s+)?(posterior al dia|a partir del dia|desde el dia|a partir del|desde el|proxima recarga\\s*:?)\\s*(\\d{1,4}[/-]\\d{1,2}[/-]\\d{2,4})").matcher(text);
                if (next.find()) {
                    LocalDate date = parseDate(next.group(2));
                    if (next.group(1).startsWith("posterior")) date = date.plusDays(1);
                    nextRecharge = date.atStartOfDay(ZoneId.of("America/Havana")).toInstant().toEpochMilli();
                    if (next.find()) throw new ParseException();
                }
            } else {
                // One source can carry multiple restricted buckets. Do not add these together.
                Matcher data = DATA.matcher(text);
                int previousEnd = 0;
                List<int[]> ranges = new ArrayList<>();
                List<String> amounts = new ArrayList<>(), units = new ArrayList<>();
                while (data.find()) { ranges.add(new int[]{data.start(), data.end()}); amounts.add(data.group(1)); units.add(data.group(2)); }
                for (int i = 0; i < ranges.size(); i++) {
                    int[] range = ranges.get(i);
                    String context = text.substring(previousEnd, range[0]);
                    int nextStart = i + 1 < ranges.size() ? ranges.get(i + 1)[0] : text.length();
                    String suffix = text.substring(range[1], nextStart).split("[;\\n]|,(?=\\s*[a-z])", 2)[0];
                    String suffixKind = bucket(suffix);
                    String kind = suffixKind != null ? suffixKind : bucket(context);
                    if (kind == null && ranges.size() == 1 && query == CubacelQuery.DATA) kind = "general";
                    if (kind == null || !kinds.add(kind)) throw new ParseException();
                    long multiplier = switch (units.get(i).toUpperCase(Locale.ROOT)) {
                        case "GB" -> 1_000_000_000L;
                        case "MB" -> 1_000_000L;
                        default -> 1_000L;
                    };
                    long bytes = decimal(amounts.get(i)).multiply(BigDecimal.valueOf(multiplier)).longValueExact();
                    int end = i + 1 < ranges.size() ? ranges.get(i + 1)[0] : text.length();
                    Long bucketExpiry = expiry(text.substring(range[1], end));
                    bundles.add(new CarrierReading.Bundle(kind, bytes, false, bucketExpiry));
                    previousEnd = range[1];
                }
                // A time/application-limited unlimited bonus is not generic unlimited data.
                if (text.contains("ilimitad")) {
                    if (!bundles.isEmpty()) throw new ParseException();
                    String kind = bucket(text);
                    if (kind == null) throw new ParseException();
                    bundles.add(new CarrierReading.Bundle(kind, null, true, expiry(text)));
                }
                Matcher clock = Pattern.compile("(?<![\\d:])(\\d{1,3}):(\\d{2}):(\\d{2})(?![\\d:])").matcher(text);
                if (clock.find()) {
                    if (Integer.parseInt(clock.group(2)) > 59 || Integer.parseInt(clock.group(3)) > 59) throw new ParseException();
                    voice = Long.parseLong(clock.group(1)) * 3600 + Long.parseLong(clock.group(2)) * 60 + Long.parseLong(clock.group(3));
                    if (clock.find()) throw new ParseException();
                } else {
                    Matcher minutes = VOICE.matcher(text);
                    if (minutes.find()) {
                        voice = decimal(minutes.group(1)).multiply(BigDecimal.valueOf(60)).longValueExact();
                        if (minutes.find()) throw new ParseException();
                        Matcher seconds = Pattern.compile("(?<![\\d.,-])(\\d{1,2})\\s*(?:segundos?|seg)\\b").matcher(text);
                        if (seconds.find()) { int value = Integer.parseInt(seconds.group(1)); if (value > 59) throw new ParseException(); voice += value; }
                    }
                }
                Matcher messages = SMS.matcher(text);
                if (messages.find()) { sms = Long.parseLong(messages.group(1)); if (messages.find()) throw new ParseException(); }
                Matcher smsLabel = Pattern.compile("\\bsms\\s*:\\s*(\\d{1,9})(?!\\d)").matcher(text);
                if (smsLabel.find()) {
                    if (sms != null) throw new ParseException();
                    sms = Long.parseLong(smsLabel.group(1));
                    if (smsLabel.find()) throw new ParseException();
                }
            }
            // Multiple validity dates must stay attached to their own bundles; never guess line expiry.
            Matcher dates = EXPIRY.matcher(text);
            int dateCount = 0;
            while (dates.find()) dateCount++;
            if (dateCount <= 1) expiry = expiry(text);
            if (query == CubacelQuery.BALANCE) {
                Matcher active = Pattern.compile("linea activa hasta\\s+(\\d{1,4}[/-]\\d{1,2}[/-]\\d{2,4})").matcher(text);
                if (active.find()) { closesAt = expiry; expiry = expiry("vence " + active.group(1)); }
            }
            if (query == CubacelQuery.BALANCE && balance == null
                    || query == CubacelQuery.DATA && bundles.isEmpty()
                    || query == CubacelQuery.VOICE && voice == null
                    || query == CubacelQuery.SMS && sms == null
                    || query == CubacelQuery.BONUSES_AND_USD_PLANS && bundles.isEmpty() && voice == null && sms == null
                    || query == CubacelQuery.RECHARGE_STATUS && rechargeReceived == null && rechargeAvailable == null && nextRecharge == null) throw new ParseException();
            return new CarrierReading(query, receivedAt, balance, voice, sms, bundles, expiry, rechargeReceived, rechargeAvailable, "USSD", null, closesAt, nextRecharge);
        } catch (ArithmeticException | IllegalArgumentException | java.time.DateTimeException exception) { throw new ParseException(); }
    }

    /** Observed carrier SMS structures, tested with invented amounts and redacted numbers. */
    public List<CarrierReading> parseSms(CharSequence body, long at) throws ParseException {
        if (body == null || body.length() == 0 || body.length() > 4096) throw new ParseException();
        String text = Normalizer.normalize(body, Normalizer.Form.NFKD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replace('\u00a0', ' ');
        if (text.contains("promo") || text.contains("si recib") || text.contains("solicite") || text.contains("oferta")) throw new ParseException();
        try {
            if (text.contains("informa:") || text.contains("descuento del combo")) throw new ParseException();
            CarrierReading.Purchase purchase = null;
            // Both current and older receipt prefixes observed on the device.
            Matcher purchasePrefix = Pattern.compile("(?:se ha comprado plan|ha recibido plan|ha comprado (?:un plan de )?)\\s*(.*?)(?:valido por)\\s+(\\d{1,3}) dias").matcher(text);
            if (purchasePrefix.find()) {
                String resources = purchasePrefix.group(1);
                int days = Integer.parseInt(purchasePrefix.group(2));
                if (days == 0 || days > 366) throw new ParseException();
                Matcher quota = DATA.matcher(resources);
                Long bytes = null, lte = null, minutes = null, messages = null;
                while (quota.find()) {
                    long value = decimal(quota.group(1)).multiply(BigDecimal.valueOf(quota.group(2).equalsIgnoreCase("gb") ? 1_000_000_000L : quota.group(2).equalsIgnoreCase("mb") ? 1_000_000L : 1_000L)).longValueExact();
                    String after = resources.substring(quota.end());
                    if (after.stripLeading().startsWith("lte")) { if (lte != null) throw new ParseException(); lte = value; }
                    else { if (bytes != null) throw new ParseException(); bytes = value; }
                }
                Matcher mins = VOICE.matcher(resources);
                if (mins.find()) minutes = decimal(mins.group(1)).multiply(BigDecimal.valueOf(60)).longValueExact();
                Matcher sms = SMS.matcher(resources);
                if (sms.find()) messages = Long.parseLong(sms.group(1));
                if (bytes == null && lte == null && minutes == null && messages == null) throw new ParseException();
                purchase = new CarrierReading.Purchase(bytes, lte, minutes, messages, days, text.contains("primer uso"));
            }
            Matcher actual = Pattern.compile("saldo (?:actual|principal)\\s*:?\\s*" + NUMBER + "\\s*(?:cup|mn)?\\b").matcher(text);
            String balance = null;
            if (actual.find()) {
                String match = actual.group();
                // Bare monetary amounts in older pre-CUP receipts are ambiguous (legacy CUC).
                boolean explicitCup = match.contains("cup") || match.contains("mn");
                if (explicitCup || purchase != null && at >= 1609459200000L && !text.contains("cuc")) balance = decimal(actual.group(1)).toPlainString();
                if (actual.find()) throw new ParseException();
            }
            if (balance != null || purchase != null) {
                if (purchase == null && !text.startsWith("usted ha transferido") && !text.startsWith("usted ha recibido")
                        && !text.startsWith("saldo ") && !text.startsWith("su saldo")) throw new ParseException();
                Matcher active = Pattern.compile("linea activa hasta\\s+(\\d{4}-\\d{2}-\\d{2})").matcher(text);
                Long activeUntil = active.find() ? expiry("vence " + active.group(1)) : null;
                Long closesAt = expiry(text);
                return List.of(new CarrierReading(CubacelQuery.BALANCE, at, balance, null, null,
                        List.of(), activeUntil, null, null).asSms(purchase, closesAt));
            }
            Matcher credited = Pattern.compile("^(?:su cuenta ha sido recargada con|usted recibio)\\s+" + NUMBER + "\\s*cup\\b").matcher(text);
            if (credited.find()) {
                // Received recharge amount is not necessarily the current balance.
                return List.of(new CarrierReading(CubacelQuery.BALANCE, at, null, null, null,
                        List.of(), null, decimal(credited.group(1)).toPlainString(), null).asSms(null, null));
            }
            if (!text.matches("(?s)^(?:su saldo|saldo[: ]|dispone|usted dispone|le quedan|restante|datos:|internet:).*")) throw new ParseException();
            List<CarrierReading> results = new ArrayList<>();
            for (CubacelQuery query : List.of(CubacelQuery.BALANCE, CubacelQuery.DATA, CubacelQuery.VOICE, CubacelQuery.SMS)) {
                try { results.add(parse(query, body, at).asSms(null, null)); }
                catch (ParseException ignored) { }
            }
            if (results.isEmpty()) throw new ParseException();
            return results;
        } catch (ArithmeticException | IllegalArgumentException | java.time.DateTimeException ignored) { throw new ParseException(); }
    }

    private static BigDecimal decimal(String value) { return new BigDecimal(value.replace(',', '.')).stripTrailingZeros(); }
    private static String bucket(String context) {
        // Use the nearest resource label, not a label belonging to a preceding bucket.
        String[] parts = context.split("[;\\n]|,(?=\\s*[a-z])");
        String last = parts[parts.length - 1];
        if (last.contains("whatsapp")) return "whatsapp";
        if (last.contains("facebook")) return "facebook";
        if (last.contains("nacional") || last.contains(".cu")) return "national";
        if (last.contains("nauta") || last.contains("correo")) return "mail";
        if (last.contains("todus")) return "todus";
        if (last.contains("nocturn") || last.contains("12am") || last.contains("7am")) return "night";
        if (last.contains("lte") || last.contains("4g")) return "lte";
        if (last.contains("todas las redes") || last.contains("internet") || last.matches("(?s).*\\bdatos\\s*[:=]?\\s*")) return "general";
        return null;
    }
    private static Long expiry(String text) throws ParseException {
        Matcher matcher = EXPIRY.matcher(text);
        if (!matcher.find()) return null;
        String date = matcher.group(1);
        if (matcher.find()) throw new ParseException();
        return parseDate(date).plusDays(1).atStartOfDay(ZoneId.of("America/Havana")).toInstant().toEpochMilli();
    }
    private static LocalDate parseDate(String raw) throws ParseException {
        String date = raw.replace('-', '/');
        if (date.matches("\\d{1,2}/\\d{1,2}/\\d{2}")) {
            int split = date.lastIndexOf('/');
            date = date.substring(0, split + 1) + "20" + date.substring(split + 1);
        }
        if (!date.matches("(?:\\d{4}/\\d{1,2}/\\d{1,2}|\\d{1,2}/\\d{1,2}/\\d{4})")) throw new ParseException();
        String pattern = date.matches("\\d{4}/.*") ? "uuuu/M/d" : "d/M/uuuu";
        return LocalDate.parse(date, DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT));
    }
}
