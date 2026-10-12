package net.timafe.triptale.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TripTest {

    private static final LocalDate START = LocalDate.of(2026, 6, 1);

    private static Trip trip(LocalDate start, LocalDate end) {
        return new Trip(2026, "Tour", "Tour", start, end, "");
    }

    @Test
    void activityIsNormalizedAndOptional() {
        assertEquals("cycling", new Trip(2026, "Tour", "Tour", START, null, "", " Cycling ").activity());
        assertEquals("Kayaking", new Trip(2026, "Tour", "Tour", START, null, "", "Kayaking").activity());
        assertNull(new Trip(2026, "Tour", "Tour", START, null, "", " ").activity());
        assertNull(trip(START, null).activity());
    }

    @Test
    void dayNumberIsOneBasedFromStartDate() {
        Trip trip = trip(START, START.plusDays(9));
        assertEquals(1L, trip.dayNumber(START));
        assertEquals(5L, trip.dayNumber(START.plusDays(4)));
    }

    @Test
    void dayNumberIsNullWithoutStartDateOrDate() {
        assertNull(trip(null, null).dayNumber(START));
        assertNull(trip(START, null).dayNumber(null));
    }

    @Test
    void totalDaysCountsStartAndEndInclusively() {
        assertEquals(10L, trip(START, START.plusDays(9)).totalDays());
    }

    @Test
    void totalDaysIsOneForSingleDayTrip() {
        assertEquals(1L, trip(START, START).totalDays());
    }

    @Test
    void totalDaysIsNullWithoutEndDate() {
        assertNull(trip(START, null).totalDays());
    }

    @Test
    void totalDaysIsNullWithoutStartDate() {
        assertNull(trip(null, START).totalDays());
    }
}
