package net.timafe.triptale.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.timafe.triptale.config.TripTaleProperties;
import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.domain.Trip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Component
public class MarkdownStore {

    private static final Logger log = LoggerFactory.getLogger(MarkdownStore.class);
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter FILE_WEEKDAY = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH);
    private static final String FRONTMATTER_DELIM = "---";

    private final TripTaleProperties props;
    private final ObjectMapper yaml;

    public MarkdownStore(TripTaleProperties props) {
        this.props = props;
        this.yaml = new ObjectMapper(new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES))
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public Path dataDir() {
        Path p = props.resolvedDataDir();
        ensureDir(p);
        ensureDir(p.resolve("trips"));
        return p;
    }

    public Path tripDir(String slug) {
        return dataDir().resolve("trips").resolve(slug);
    }

    public Path entriesDir(String slug) {
        return tripDir(slug).resolve("entries");
    }

    public Path entryFile(String slug, LocalDate date) {
        return entriesDir(slug).resolve(date.format(FILE_DATE) + "_" + date.format(FILE_WEEKDAY) + ".md");
    }

    public List<Trip> listTrips() {
        Path trips = dataDir().resolve("trips");
        if (!Files.isDirectory(trips)) return List.of();
        try (Stream<Path> s = Files.list(trips)) {
            return s.filter(Files::isDirectory)
                    .map(p -> loadTrip(p.getFileName().toString()).orElse(null))
                    .filter(t -> t != null)
                    .sorted(Comparator.comparing(Trip::slug))
                    .toList();
        } catch (IOException e) {
            throw new StorageException("Failed to list trips", e);
        }
    }

    public Optional<Trip> loadTrip(String slug) {
        Path tripYml = tripDir(slug).resolve("trip.yml");
        if (!Files.exists(tripYml)) return Optional.empty();
        try {
            Map<String, Object> data = yaml.readValue(Files.readString(tripYml), Map.class);
            return Optional.of(new Trip(
                    slug,
                    asString(data.get("name")),
                    asDate(data.get("startDate")),
                    asString(data.get("description"))
            ));
        } catch (IOException e) {
            throw new StorageException("Failed to load trip: " + slug, e);
        }
    }

    public void saveTrip(Trip trip) {
        ensureDir(tripDir(trip.slug()));
        ensureDir(entriesDir(trip.slug()));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", trip.name());
        data.put("startDate", trip.startDate() == null ? null : trip.startDate().toString());
        data.put("description", trip.description());
        try {
            Files.writeString(tripDir(trip.slug()).resolve("trip.yml"), yaml.writeValueAsString(data));
        } catch (IOException e) {
            throw new StorageException("Failed to save trip: " + trip.slug(), e);
        }
    }

    public boolean entryExists(String slug, LocalDate date) {
        return Files.exists(entryFile(slug, date));
    }

    public DiaryEntry loadEntry(String slug, LocalDate date) {
        Path file = entryFile(slug, date);
        if (!Files.exists(file)) return DiaryEntry.empty(date);
        try {
            String content = Files.readString(file);
            return parseEntry(date, content);
        } catch (IOException e) {
            throw new StorageException("Failed to load entry: " + slug + "/" + date, e);
        }
    }

    public void saveEntry(String slug, DiaryEntry entry) {
        ensureDir(entriesDir(slug));
        Map<String, Object> fm = new LinkedHashMap<>();
        fm.put("date", entry.date().toString());
        if (entry.distance() != null) fm.put("distance", entry.distance());
        if (entry.altitudeMeters() != null) fm.put("altitude", entry.altitudeMeters());
        if (entry.route() != null && !entry.route().isBlank()) fm.put("route", entry.route());
        if (entry.trackUrl() != null && !entry.trackUrl().isBlank()) fm.put("trackurl", entry.trackUrl());
        try {
            String body = entry.tales() == null ? "" : entry.tales();
            String content = FRONTMATTER_DELIM + "\n" + yaml.writeValueAsString(fm) + FRONTMATTER_DELIM + "\n\n" + body;
            Files.writeString(entryFile(slug, entry.date()), content);
        } catch (IOException e) {
            throw new StorageException("Failed to save entry: " + slug + "/" + entry.date(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Local preferences (gitignored, not synced)
    // -------------------------------------------------------------------------

    private static final String PREFS_FILE = "prefs.yml";
    private static final String PREFS_LAST_TRIP_KEY = "lastTripSlug";
    private static final String PREFS_COMMENT =
            "# Local preferences — machine-specific, not committed to git.\n" +
            "# This file is listed in .gitignore and intentionally excluded from sync.\n";

    public void saveLastTripSlug(String slug) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(PREFS_LAST_TRIP_KEY, slug);
        try {
            String content = PREFS_COMMENT + yaml.writeValueAsString(data);
            Files.writeString(dataDir().resolve(PREFS_FILE), content);
        } catch (IOException e) {
            log.warn("Could not write {}: {}", PREFS_FILE, e.getMessage());
        }
    }

    public Optional<String> loadLastTripSlug() {
        Path prefs = dataDir().resolve(PREFS_FILE);
        if (!Files.exists(prefs)) return Optional.empty();
        try {
            String raw = Files.readString(prefs);
            // Strip leading comment lines before YAML parsing
            String yaml_ = raw.lines()
                    .filter(l -> !l.startsWith("#"))
                    .reduce("", (a, b) -> a + b + "\n");
            Map<String, Object> data = yaml.readValue(yaml_, Map.class);
            return Optional.ofNullable(asString(data.get(PREFS_LAST_TRIP_KEY)));
        } catch (IOException e) {
            log.warn("Could not read {}: {}", PREFS_FILE, e.getMessage());
            return Optional.empty();
        }
    }

    public List<LocalDate> listEntryDates(String slug) {
        Path dir = entriesDir(slug);
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.list(dir)) {
            List<LocalDate> dates = new ArrayList<>();
            s.filter(p -> p.toString().endsWith(".md")).forEach(p -> {
                String name = p.getFileName().toString();
                String stem = name.substring(0, name.length() - 3);
                int sep = stem.indexOf('_');
                String dateStr = sep < 0 ? stem : stem.substring(0, sep);
                try {
                    dates.add(LocalDate.parse(dateStr));
                } catch (Exception ignored) {
                    log.debug("Skipping non-date entry file: {}", name);
                }
            });
            dates.sort(Comparator.naturalOrder());
            return dates;
        } catch (IOException e) {
            throw new StorageException("Failed to list entries for " + slug, e);
        }
    }

    private DiaryEntry parseEntry(LocalDate date, String content) throws IOException {
        if (!content.startsWith(FRONTMATTER_DELIM)) {
            return DiaryEntry.builder(date).tales(content).build();
        }
        int end = content.indexOf("\n" + FRONTMATTER_DELIM, FRONTMATTER_DELIM.length());
        if (end < 0) {
            return DiaryEntry.builder(date).tales(content).build();
        }
        String fm = content.substring(FRONTMATTER_DELIM.length(), end).trim();
        String body = content.substring(end + ("\n" + FRONTMATTER_DELIM).length()).stripLeading();
        Map<String, Object> data = yaml.readValue(fm, Map.class);
        return DiaryEntry.builder(date)
                .distance(asDouble(data.get("distance")))
                .altitudeMeters(asDouble(data.get("altitude")))
                .route(asString(data.get("route")))
                .trackUrl(asString(data.get("trackurl")))
                .tales(body)
                .build();
    }

    private static void ensureDir(Path p) {
        try {
            Files.createDirectories(p);
        } catch (IOException e) {
            throw new StorageException("Failed to create directory: " + p, e);
        }
    }

    private static String asString(Object o) { return o == null ? null : o.toString(); }
    private static Double asDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        return Double.parseDouble(o.toString());
    }
    private static LocalDate asDate(Object o) {
        if (o == null) return null;
        if (o instanceof LocalDate d) return d;
        return LocalDate.parse(o.toString());
    }
}
