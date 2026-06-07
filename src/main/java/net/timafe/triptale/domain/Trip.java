package net.timafe.triptale.domain;

import java.time.LocalDate;

public record Trip(
        String slug,
        String name,
        LocalDate startDate,
        String description
) {
}
