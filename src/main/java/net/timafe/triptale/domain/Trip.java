package net.timafe.triptale.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record Trip(
        int year,
        String slug,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        String description
) {
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
