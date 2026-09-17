package net.timafe.triptale.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.domain.TripRef;
import net.timafe.triptale.util.Markdown;
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
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
public class MarkdownStore {

    private static final Logger log = LoggerFactory.getLogger(MarkdownStore.class);
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter FILE_WEEKDAY = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH);
    private static final String FRONTMATTER_DELIM = "---";
    /** Tolaria (https://github.com/refactoringhq/tolaria) note type assigned to every diary entry. */
    private static final String ENTRY_TYPE = "Tale";
    /** Tolaria note type assigned to every trip README.md. */
    private static final String TRIP_TYPE = "Trip";
    private static final Pattern YEAR_DIR = Pattern.compile("\\d{4}");

    private final SettingsStore settingsStore;
    private final ObjectMapper yaml;

    public MarkdownStore(SettingsStore settingsStore) {
        this.settingsStore = settingsStore;
        this.yaml = new ObjectMapper(new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES))
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public Path dataDir() {
        Path p = settingsStore.load().resolvedDataDir()
                .orElseThrow(() -> new StorageException(
                        "Data directory not configured — set it via Edit Settings"));
        ensureDir(p);
        return p;
    }

    public Path tripDir(TripRef ref) {
        return dataDir().resolve(Integer.toString(ref.year())).resolve(ref.slug());
    }

    public Path entriesDir(TripRef ref) {
        return tripDir(ref);
    }

    public Path entryFile(TripRef ref, LocalDate date) {
        return entriesDir(ref).resolve(date.format(FILE_DATE) + "-" + date.format(FILE_WEEKDAY) + ".md");
    }

    /** Top-level year directories directly under the data dir (4-digit names only), descending. */
    public List<Integer> listYears() {
        Path root = dataDir();
        if (!Files.isDirectory(root)) return List.of();
        try (Stream<Path> s = Files.list(root)) {
            return s.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> YEAR_DIR.matcher(name).matches())
                    .map(Integer::parseInt)
                    .sorted(Comparator.reverseOrder())
                    .toList();
        } catch (IOException e) {
            throw new StorageException("Failed to list years", e);
        }
    }

    /** Trips for a given year, sorted by startDate descending (most recent first). */
    public List<Trip> listTrips(int year) {
        Path yearDir = dataDir().resolve(Integer.toString(year));
        if (!Files.isDirectory(yearDir)) return List.of();
        try (Stream<Path> s = Files.list(yearDir)) {
            return s.filter(Files::isDirectory)
                    .map(p -> loadTrip(new TripRef(year, p.getFileName().toString())).orElse(null))
                    .filter(t -> t != null)
                    .sorted(Comparator.comparing(Trip::startDate,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        } catch (IOException e) {
            throw new StorageException("Failed to list trips for " + year, e);
        }
    }

    public boolean tripExists(TripRef ref) {
        return Files.isDirectory(tripDir(ref));
    }

    public Optional<Trip> loadTrip(TripRef ref) {
        Path readme = tripDir(ref).resolve("README.md");
        if (!Files.exists(readme)) return Optional.empty();
        try {
            String content = Files.readString(readme);
            if (!content.startsWith(FRONTMATTER_DELIM)) {
                return Optional.of(new Trip(ref.year(), ref.slug(), null, null, content));
            }
            int end = content.indexOf("\n" + FRONTMATTER_DELIM, FRONTMATTER_DELIM.length());
            if (end < 0) {
                return Optional.of(new Trip(ref.year(), ref.slug(), null, null, content));
            }
            String fm = content.substring(FRONTMATTER_DELIM.length(), end).trim();
            String body = content.substring(end + ("\n" + FRONTMATTER_DELIM).length()).stripLeading();
            Map<String, Object> data = yaml.readValue(fm, Map.class);
            String name = null;
            String description = body;
            if (body.startsWith("# ")) {
                int nl = body.indexOf('\n');
                name = (nl < 0 ? body.substring(2) : body.substring(2, nl)).trim();
                description = (nl < 0 ? "" : body.substring(nl + 1)).stripLeading();
            }
            return Optional.of(new Trip(
                    ref.year(),
                    ref.slug(),
                    name,
                    asDate(data.get("startDate")),
                    description
            ));
        } catch (IOException e) {
            throw new StorageException("Failed to load trip: " + ref.path(), e);
        }
    }

    public void saveTrip(Trip trip) {
        TripRef ref = trip.ref();
        ensureDir(tripDir(ref));
        ensureDir(entriesDir(ref));
        // Keys are inserted in alphabetical order so the serialized YAML is stable and diffs stay minimal.
        Map<String, Object> fm = new LinkedHashMap<>();
        fm.put("startDate", trip.startDate() == null ? null : trip.startDate().toString());
        fm.put("type", TRIP_TYPE);
        try {
            String description = trip.description() == null ? "" : trip.description();
            String body = "# " + trip.name() + "\n\n" + description;
            String content = FRONTMATTER_DELIM + "\n" + yaml.writeValueAsString(fm) + FRONTMATTER_DELIM + "\n\n" + body;
            Files.writeString(tripDir(ref).resolve("README.md"), content);
        } catch (IOException e) {
            throw new StorageException("Failed to save trip: " + ref.path(), e);
        }
    }

    public boolean entryExists(TripRef ref, LocalDate date) {
        return Files.exists(entryFile(ref, date));
    }

    public DiaryEntry loadEntry(TripRef ref, LocalDate date) {
        Path file = entryFile(ref, date);
        if (!Files.exists(file)) return DiaryEntry.empty(date);
        try {
            String content = Files.readString(file);
            return parseEntry(date, content);
        } catch (IOException e) {
            throw new StorageException("Failed to load entry: " + ref.path() + "/" + date, e);
        }
    }

    public String readEntrySource(TripRef ref, LocalDate date) {
        try {
            return Files.readString(entryFile(ref, date));
        } catch (IOException e) {
            throw new StorageException("Failed to read entry source: " + ref.path() + "/" + date, e);
        }
    }

    public void saveEntry(TripRef ref, DiaryEntry entry) {
        ensureDir(entriesDir(ref));
        // Keys are inserted in alphabetical order so the serialized YAML is stable and diffs stay minimal.
        Map<String, Object> fm = new LinkedHashMap<>();
        if (entry.altitudeMeters() != null) fm.put("altitude", entry.altitudeMeters());
        fm.put("belongs_to", "[[" + ref.path() + "/README]]");
        fm.put("date", entry.date().toString());
        if (entry.distance() != null) fm.put("distance", entry.distance());
        if (entry.trackUrl() != null && !entry.trackUrl().isBlank()) fm.put("trackurl", entry.trackUrl());
        fm.put("type", ENTRY_TYPE);
        try {
            String tales = entry.tales() == null ? "" : entry.tales();
            String body = "# " + entry.title() + "\n\n" + Markdown.demoteH1(tales);
            String content = FRONTMATTER_DELIM + "\n" + yaml.writeValueAsString(fm) + FRONTMATTER_DELIM + "\n\n" + body;
            Files.writeString(entryFile(ref, entry.date()), content);
        } catch (IOException e) {
            throw new StorageException("Failed to save entry: " + ref.path() + "/" + entry.date(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Repo-local internal state (gitignored, not synced, never UI-edited)
    // -------------------------------------------------------------------------

    private static final String STATE_FILE = ".state.yml";
    private static final String STATE_LAST_TRIP_KEY = "lastTripPath";
    private static final String STATE_COMMENT =
            "# Repo-local internal state — machine-specific, not committed to git.\n" +
            "# This file is listed in .gitignore and intentionally excluded from sync.\n" +
            "# Written programmatically only; never exposed in any settings UI.\n";

    /** Reads the whole .state.yml as a key-value map (empty map if the file doesn't exist). */
    public Map<String, Object> loadState() {
        Path state = dataDir().resolve(STATE_FILE);
        if (!Files.exists(state)) return new LinkedHashMap<>();
        try {
            String raw = Files.readString(state);
            // Strip leading comment lines before YAML parsing
            String yaml_ = raw.lines()
                    .filter(l -> !l.startsWith("#"))
                    .reduce("", (a, b) -> a + b + "\n");
            Map<String, Object> data = yaml.readValue(yaml_, Map.class);
            return data == null ? new LinkedHashMap<>() : new LinkedHashMap<>(data);
        } catch (IOException e) {
            log.warn("Could not read {}: {}", STATE_FILE, e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /** Merges the given key into the existing .state.yml and writes the whole file back. */
    public void saveState(String key, Object value) {
        Map<String, Object> data = loadState();
        data.put(key, value);
        Path statePath = dataDir().resolve(STATE_FILE);
        try {
            String content = STATE_COMMENT + yaml.writeValueAsString(data);
            Files.writeString(statePath, content);
            log.info("Saved state '{}' to {}", key, statePath);
        } catch (IOException e) {
            log.warn("Could not write {}: {}", statePath, e.getMessage());
        }
    }

    public void saveLastTripPath(TripRef ref) {
        saveState(STATE_LAST_TRIP_KEY, ref.path());
    }

    public Optional<TripRef> loadLastTripPath() {
        String raw = asString(loadState().get(STATE_LAST_TRIP_KEY));
        if (raw == null) return Optional.empty();
        try {
            return Optional.of(TripRef.parse(raw));
        } catch (RuntimeException e) {
            log.warn("Ignoring malformed {}: '{}'", STATE_LAST_TRIP_KEY, raw);
            return Optional.empty();
        }
    }

    public List<LocalDate> listEntryDates(TripRef ref) {
        Path dir = entriesDir(ref);
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.list(dir)) {
            List<LocalDate> dates = new ArrayList<>();
            s.filter(p -> p.toString().endsWith(".md")).forEach(p -> {
                String name = p.getFileName().toString();
                String stem = name.substring(0, name.length() - 3);
                String dateStr = stem.length() < 10 ? stem : stem.substring(0, 10);
                try {
                    dates.add(LocalDate.parse(dateStr));
                } catch (Exception ignored) {
                    log.debug("Skipping non-date entry file: {}", name);
                }
            });
            dates.sort(Comparator.naturalOrder());
            return dates;
        } catch (IOException e) {
            throw new StorageException("Failed to list entries for " + ref.path(), e);
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
        Markdown.TitleAndBody titleAndBody = Markdown.extractTitle(body);
        String title = titleAndBody.title();
        boolean hasTitle = title != null && !title.isBlank();
        String tales = hasTitle ? titleAndBody.remainder() : body;
        return DiaryEntry.builder(date)
                .distance(asDouble(data.get("distance")))
                .altitudeMeters(asDouble(data.get("altitude")))
                .title(title)
                .trackUrl(asString(data.get("trackurl")))
                .tales(tales)
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
