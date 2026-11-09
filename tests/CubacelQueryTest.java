import com.simple.mobile.domain.carrier.CubacelQuery;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Arrays;

public final class CubacelQueryTest {
    public static void main(String[] args) {
        var codes = Arrays.stream(CubacelQuery.values()).map(CubacelQuery::code).collect(Collectors.toSet());
        if (!codes.equals(Set.of("*222#", "*222*328#", "*222*266#", "*222*869#", "*222*767#", "*222*732#")))
            throw new AssertionError("Unexpected Cubacel command catalog");
        if (codes.size() != CubacelQuery.values().length) throw new AssertionError("Duplicate code");
        System.out.println("Cubacel catalog checks passed");
    }
}
