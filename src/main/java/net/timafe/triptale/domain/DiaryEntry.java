package net.timafe.triptale.domain;

import java.time.LocalDate;

public record DiaryEntry(
        LocalDate date,
        Double distance,
        Double altitudeMeters,
        String title,
        String trackUrl,
        Double startLat,
        Double startLon,
        Double stopLat,
        Double stopLon,
        String tales
) {
    public static final String DEFAULT_TITLE = "Untitled";

    public static Builder builder(LocalDate date) { return new Builder(date); }
    public static DiaryEntry empty(LocalDate date) { return builder(date).build(); }

    public static final class Builder {
        private final LocalDate date;
        private Double distance;
        private Double altitudeMeters;
        private String title = DEFAULT_TITLE;
        private String trackUrl;
        private Double startLat;
        private Double startLon;
        private Double stopLat;
        private Double stopLon;
        private String tales = "";

        private Builder(LocalDate date) { this.date = date; }

        public Builder distance(Double v)       { this.distance = v; return this; }
        public Builder altitudeMeters(Double v) { this.altitudeMeters = v; return this; }
        public Builder title(String v)          { this.title = (v != null && !v.isBlank()) ? v : DEFAULT_TITLE; return this; }
        public Builder trackUrl(String v)       { this.trackUrl = (v != null && !v.isBlank()) ? v : null; return this; }
        public Builder startLat(Double v)       { this.startLat = v; return this; }
        public Builder startLon(Double v)       { this.startLon = v; return this; }
        public Builder stopLat(Double v)        { this.stopLat = v; return this; }
        public Builder stopLon(Double v)        { this.stopLon = v; return this; }
        public Builder tales(String v)          { this.tales = v != null ? v : ""; return this; }

        public DiaryEntry build() {
            return new DiaryEntry(date, distance, altitudeMeters, title, trackUrl,
                    startLat, startLon, stopLat, stopLon, tales);
        }
    }
}
