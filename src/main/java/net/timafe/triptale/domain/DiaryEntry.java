package net.timafe.triptale.domain;

import java.time.LocalDate;

public record DiaryEntry(
        LocalDate date,
        Double distance,
        Double altitudeMeters,
        String route,
        String notes
) {
    public static final String DEFAULT_ROUTE = "From → To";

    public static Builder builder(LocalDate date) { return new Builder(date); }
    public static DiaryEntry empty(LocalDate date) { return builder(date).build(); }

    public static final class Builder {
        private final LocalDate date;
        private Double distance;
        private Double altitudeMeters;
        private String route = DEFAULT_ROUTE;
        private String notes = "";

        private Builder(LocalDate date) { this.date = date; }

        public Builder distance(Double v)       { this.distance = v; return this; }
        public Builder altitudeMeters(Double v) { this.altitudeMeters = v; return this; }
        public Builder route(String v)          { this.route = (v != null && !v.isBlank()) ? v : DEFAULT_ROUTE; return this; }
        public Builder notes(String v)          { this.notes = v != null ? v : ""; return this; }

        public DiaryEntry build() {
            return new DiaryEntry(date, distance, altitudeMeters, route, notes);
        }
    }
}
