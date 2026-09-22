package net.timafe.triptale.domain;

import java.time.LocalDate;

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
}
