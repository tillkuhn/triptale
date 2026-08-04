package net.timafe.triptale.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SaveTargetTest {

    @Test
    void forTripChange_keepsPreviousTripAtCurrentDate() {
        // Trip switch: the date picker hasn't moved yet, so unsaved edits belong to the
        // previous trip at whatever date is still showing.
        SaveTarget target = SaveTarget.forTripChange("alps-2024", LocalDate.of(2024, 6, 4));

        assertEquals("alps-2024", target.tripSlug());
        assertEquals(LocalDate.of(2024, 6, 4), target.date());
    }

    @Test
    void forDateChange_keepsCurrentTripAtPreviousDate() {
        // Date switch (e.g. Next Day): JavaFX already advanced the DatePicker's value before the
        // listener fired, so unsaved edits belong to the previous date, not datePicker.getValue().
        SaveTarget target = SaveTarget.forDateChange("alps-2024", LocalDate.of(2024, 6, 4));

        assertEquals("alps-2024", target.tripSlug());
        assertEquals(LocalDate.of(2024, 6, 4), target.date());
    }

    @Test
    void factoriesProduceDistinctTargetsForDifferentInputs() {
        SaveTarget tripChange = SaveTarget.forTripChange("old-trip", LocalDate.of(2024, 6, 4));
        SaveTarget dateChange = SaveTarget.forDateChange("current-trip", LocalDate.of(2024, 6, 3));

        assertEquals("old-trip", tripChange.tripSlug());
        assertEquals("current-trip", dateChange.tripSlug());
        assertEquals(LocalDate.of(2024, 6, 3), dateChange.date());
    }
}
