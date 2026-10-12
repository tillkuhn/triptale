package net.timafe.triptale.domain;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SequencedMap;
import java.util.TreeMap;

/**
 * One diary day. {@code links} is keyed by verb (see {@link LinkKind}) and ordered by
 * {@link LinkKind#ORDER}; never null. {@code activity} is normalized by {@link Activity#normalize},
 * null means not set.
 */
public record DiaryEntry(
        LocalDate date,
        String activity,
        Double distance,
        Double altitudeMeters,
        String title,
        SequencedMap<String, Link> links,
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
        private String activity;
        private Double distance;
        private Double altitudeMeters;
        private String title = DEFAULT_TITLE;
        private final Map<String, Link> links = new TreeMap<>(LinkKind.ORDER);
        private Double startLat;
        private Double startLon;
        private Double stopLat;
        private Double stopLon;
        private String tales = "";

        private Builder(LocalDate date) { this.date = date; }

        public Builder activity(String v)       { this.activity = Activity.normalize(v); return this; }
        public Builder distance(Double v)       { this.distance = v; return this; }
        public Builder altitudeMeters(Double v) { this.altitudeMeters = v; return this; }
        public Builder title(String v)          { this.title = (v != null && !v.isBlank()) ? v : DEFAULT_TITLE; return this; }
        /** Replaces all links; keys are verbs ({@code track}, …), blank values are skipped. */
        public Builder links(Map<String, Link> v) {
            links.clear();
            if (v != null) v.forEach(this::link);
            return this;
        }
        public Builder link(String verb, Link v) {
            if (verb != null && !verb.isBlank() && v != null && !v.url().isBlank()) links.put(verb, v);
            return this;
        }
        public Builder startLat(Double v)       { this.startLat = v; return this; }
        public Builder startLon(Double v)       { this.startLon = v; return this; }
        public Builder stopLat(Double v)        { this.stopLat = v; return this; }
        public Builder stopLon(Double v)        { this.stopLon = v; return this; }
        public Builder tales(String v)          { this.tales = v != null ? v : ""; return this; }

        public DiaryEntry build() {
            return new DiaryEntry(date, activity, distance, altitudeMeters, title,
                    Collections.unmodifiableSequencedMap(new LinkedHashMap<>(links)),
                    startLat, startLon, stopLat, stopLon, tales);
        }
    }
}
