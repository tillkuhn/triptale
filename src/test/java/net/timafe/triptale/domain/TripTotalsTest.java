package net.timafe.triptale.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TripTotalsTest {

    private static Trip trip(String activity, boolean track) {
        return new Trip(2026, "t", "T", LocalDate.of(2026, 6, 1), null, "", activity, null, track);
    }

    private static DiaryEntry day(int d, String activity, Double km, Double m) {
        return DiaryEntry.builder(LocalDate.of(2026, 6, d)).activity(activity).distance(km).altitudeMeters(m).build();
    }

    private final List<DiaryEntry> entries = List.of(
            day(1, "bikepacking", 50.0, 500.0),
            day(2, "bikepacking", 60.0, null),
            day(3, "railtripping", 1000.0, null),
            day(4, "bikepacking", 0.0, 0.0));

    @Test
    void countsOnlyDefaultActivity() {
        TripTotals t = TripTotals.of(trip("bikepacking", true), entries).orElseThrow();
        assertEquals(110.0, t.distance());
        assertEquals(2, t.activeDays());
        assertEquals(500.0, t.altitude());
    }

    @Test
    void upToDateStopsAtThatDay() {
        TripTotals t = TripTotals.of(trip("bikepacking", true), entries, LocalDate.of(2026, 6, 1)).orElseThrow();
        assertEquals(50.0, t.distance());
    }

    @Test
    void emptyWithoutFlagOrActivity() {
        assertTrue(TripTotals.of(trip("bikepacking", false), entries).isEmpty());
        assertTrue(TripTotals.of(trip(null, true), entries).isEmpty());
    }
}
