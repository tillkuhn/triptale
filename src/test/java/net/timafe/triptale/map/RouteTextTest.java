package net.timafe.triptale.map;

import net.timafe.triptale.domain.DiaryEntry;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RouteTextTest {

    private static final LocalDate DAY = LocalDate.of(2026, 6, 4);

    private static DiaryEntry entry(int day, String start, String stop) {
        return DiaryEntry.builder(DAY.plusDays(day)).startPlace(start).stopPlace(stop).build();
    }

    @Test
    void collapsesConsecutiveDuplicatesAndAppendsLastStop() {
        String text = RouteText.of(List.of(
                entry(0, "Soest, DE", "Soest, DE"),
                entry(1, "Soest, DE", null),
                entry(2, "Athens, GR", "Rhodes, GR")));

        assertEquals("Soest → Athens → Rhodes", text);
    }

    @Test
    void roundTripKeepsNonConsecutiveRepeats() {
        String text = RouteText.of(List.of(
                entry(0, "Soest, DE", null),
                entry(1, "Athens, GR", null),
                entry(2, "Soest, DE", "Soest, DE")));

        assertEquals("Soest → Athens → Soest", text);
    }

    @Test
    void skipsDaysWithoutPlaceAndOmitsMissingLastStop() {
        assertEquals("A → B", RouteText.of(List.of(
                entry(0, "A", null), entry(1, null, null), entry(2, "B", null))));
    }

    @Test
    void stripsOnlyTwoLetterUppercaseCountryCode() {
        assertEquals("Soest", RouteText.stripCountry("Soest, DE"));
        assertEquals("Paris, France", RouteText.stripCountry("Paris, France"));
        assertEquals("Krung Thep Aphiwat", RouteText.stripCountry("Krung Thep Aphiwat,TH"));
    }

    @Test
    void customSeparator() {
        assertEquals("A, B", RouteText.of(List.of(entry(0, "A", "B")), ", "));
    }

    @Test
    void emptyRangeIsEmptyText() {
        assertEquals("", RouteText.of(List.of()));
    }
}
