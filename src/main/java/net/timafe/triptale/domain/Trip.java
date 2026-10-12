package net.timafe.triptale.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * {@code activity} is the default for new entries (todo 57), copied into an entry when it's first
 * created rather than inherited at read time; normalized by {@link Activity#normalize}, null = none.
 */
public record Trip(
        int year,
        String slug,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        String description,
        String activity
) {
    public Trip {
        activity = Activity.normalize(activity);
    }

    /** A trip without a default activity. */
    public Trip(int year, String slug, String name, LocalDate startDate, LocalDate endDate, String description) {
        this(year, slug, name, startDate, endDate, description, null);
    }

    public TripRef ref() {
        return new TripRef(year, slug);
    }

    /** 1-based day of the trip for {@code date}, or null if {@code startDate} or {@code date} is unset. */
    public Long dayNumber(LocalDate date) {
        if (startDate == null || date == null) return null;
        return ChronoUnit.DAYS.between(startDate, date) + 1;
    }

    /** Inclusive length of the trip ({@code endDate - startDate + 1}), or null if either date is unset. */
    public Long totalDays() {
        if (startDate == null || endDate == null) return null;
        return ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }
}
