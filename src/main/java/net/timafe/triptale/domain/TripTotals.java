package net.timafe.triptale.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;

/**
 * Distance and altitude summed over the entries whose activity is the trip's default activity
 * (todo 96). "Active" days are those with a recorded value greater than zero, so rest days don't
 * drag the averages down.
 */
public record TripTotals(String activity, double distance, int activeDays, double altitude, int activeAltitudeDays) {

    /** Empty unless the trip {@link Trip#tracksTotals() tracks totals}. */
    public static Optional<TripTotals> of(Trip trip, Collection<DiaryEntry> entries) {
        return of(trip, entries, null);
    }

    /** As {@link #of(Trip, Collection)}, counting only entries dated up to and including {@code upTo} (null = all). */
    public static Optional<TripTotals> of(Trip trip, Collection<DiaryEntry> entries, LocalDate upTo) {
        if (!trip.tracksTotals()) return Optional.empty();
        double distance = 0;
        double altitude = 0;
        int activeDays = 0;
        int activeAltitudeDays = 0;
        for (DiaryEntry e : entries) {
            if (!trip.activity().equals(Activity.normalize(e.activity()))) continue;
            if (upTo != null && e.date().isAfter(upTo)) continue;
            if (e.distance() != null && e.distance() > 0) {
                distance += e.distance();
                activeDays++;
            }
            if (e.altitudeMeters() != null && e.altitudeMeters() > 0) {
                altitude += e.altitudeMeters();
                activeAltitudeDays++;
            }
        }
        return Optional.of(new TripTotals(trip.activity(), distance, activeDays, altitude, activeAltitudeDays));
    }
}
