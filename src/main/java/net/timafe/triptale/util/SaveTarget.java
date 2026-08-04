package net.timafe.triptale.util;

import java.time.LocalDate;

/**
 * Identifies which trip/date the currently-unsaved form edits actually belong to when navigating
 * away from an entry.
 * <p>
 * JavaFX updates a {@code ComboBox}/{@code DatePicker} control's own {@code getValue()} synchronously
 * <em>before</em> its {@code ChangeListener} fires. That means once the navigate-away confirmation
 * dialog runs, re-reading the trip/date directly from the controls no longer reflects the entry the
 * unsaved edits were made against &mdash; it already reflects the new selection. Use these factory
 * methods to capture the correct target explicitly instead.
 */
public record SaveTarget(String tripSlug, LocalDate date) {

    /**
     * The trip selector changed; the date picker has not moved yet, so the unsaved edits belong to the
     * <em>previous</em> trip at the <em>current</em> date.
     */
    public static SaveTarget forTripChange(String previousTripSlug, LocalDate currentDate) {
        return new SaveTarget(previousTripSlug, currentDate);
    }

    /**
     * The date picker changed; the trip selector has not moved, so the unsaved edits belong to the
     * <em>current</em> trip at the <em>previous</em> date.
     */
    public static SaveTarget forDateChange(String currentTripSlug, LocalDate previousDate) {
        return new SaveTarget(currentTripSlug, previousDate);
    }
}
