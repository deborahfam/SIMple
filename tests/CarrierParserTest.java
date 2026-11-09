import com.simple.mobile.data.parser.ExampleCarrierParser;
import com.simple.mobile.domain.query.CarrierResponseParser;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

public final class CarrierParserTest {
    public static void main(String[] args) throws Exception {
        var parser = new ExampleCarrierParser(ZoneId.of("America/Puerto_Rico"));
        UUID account = UUID.randomUUID();
        var snapshot = parser.parse(account, Instant.EPOCH,
                "Balance: USD 12.50; Data: 1.25 GB; Minutes: 30; Expires: 2026-12-01");
        check(snapshot.accountId().equals(account));
        check(snapshot.bundles().get(0).remainingBytes() == 1_250_000_000L);
        check(snapshot.remainingVoiceSeconds() == 1800L);
        check(snapshot.balanceExpiresAt().equals(Instant.parse("2026-12-02T04:00:00Z")));
        var zero = parser.parse(account, Instant.EPOCH,
                "balance: USD 0; data: 0 MB; minutes: 0; expires: 2026-12-01");
        check(zero.bundles().get(0).remainingBytes() == 0);
        for (String invalid : new String[]{
                "Your phone 1234567890 has 50 points", "Balance: USD 1,25; Data: 1 GB; Minutes: 2; Expires: 2026-12-01",
                "Balance: USD 1; Data: 1 GB; Minutes: 2; Expires: 2026-02-30",
                "Balance: ZZZ 1; Data: 1 GB; Minutes: 2; Expires: 2026-12-01",
                "Balance: USD 1; Data: -1 GB; Minutes: 2; Expires: 2026-12-01",
                "x".repeat(4097)}) {
            try {
                parser.parse(account, Instant.EPOCH, invalid);
                throw new AssertionError("Expected rejection");
            } catch (CarrierResponseParser.UnrecognizedResponse expected) { }
        }
        System.out.println("Carrier parser checks passed");
    }
    private static void check(boolean condition) { if (!condition) throw new AssertionError(); }
}
