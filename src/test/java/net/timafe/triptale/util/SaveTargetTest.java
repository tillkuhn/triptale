package net.timafe.triptale.util;

import net.timafe.triptale.domain.TripRef;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SaveTargetTest {

    private static final TripRef ALPS_2024 = new TripRef(2024, "alps-2024");

    @Test
    void forTripChange_keepsPreviousTripAtCurrentDate() {
        // Trip switch: the date picker hasn't moved yet, so unsaved edits belong to the
        // previous trip at whatever date is still showing.
        SaveTarget target = SaveTarget.forTripChange(ALPS_2024, LocalDate.of(2024, 6, 4));

        assertEquals(ALPS_2024, target.trip());
        assertEquals(LocalDate.of(2024, 6, 4), target.date());
    }

    @Test
    void forDateChange_keepsCurrentTripAtPreviousDate() {
        // Date switch (e.g. Next Day): JavaFX already advanced the DatePicker's value before the
        // listener fired, so unsaved edits belong to the previous date, not datePicker.getValue().
        SaveTarget target = SaveTarget.forDateChange(ALPS_2024, LocalDate.of(2024, 6, 4));

        assertEquals(ALPS_2024, target.trip());
        assertEquals(LocalDate.of(2024, 6, 4), target.date());
    }

    @Test
    void factoriesProduceDistinctTargetsForDifferentInputs() {
        TripRef oldTrip = new TripRef(2024, "old-trip");
        TripRef currentTrip = new TripRef(2024, "current-trip");
        SaveTarget tripChange = SaveTarget.forTripChange(oldTrip, LocalDate.of(2024, 6, 4));
        SaveTarget dateChange = SaveTarget.forDateChange(currentTrip, LocalDate.of(2024, 6, 3));

        assertEquals(oldTrip, tripChange.trip());
        assertEquals(currentTrip, dateChange.trip());
        assertEquals(LocalDate.of(2024, 6, 3), dateChange.date());
    }
}
