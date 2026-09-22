package net.timafe.triptale.storage;

import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.config.TripTaleProperties;
import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.domain.TripRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownStoreTest {

    @TempDir
    Path tempDir;

    private MarkdownStore store;

    private static final TripRef ALPS_2025 = new TripRef(2025, "alps-2025");

    @BeforeEach
    void setUp() {
        Path settingsDir = tempDir.resolve("settings");
        Path dataDir = tempDir.resolve("data");
        TripTaleProperties props = new TripTaleProperties();
        props.setSettingsDir(settingsDir.toString());
        SettingsStore settingsStore = new SettingsStore(props);
        AppSettings settings = new AppSettings();
        settings.setDataDir(dataDir.toString());
        settingsStore.save(settings);
        store = new MarkdownStore(settingsStore);
    }

    @Test
    void dataDirCreatesRoot() {
        Path root = store.dataDir();
        assertTrue(Files.isDirectory(root));
    }

    @Test
    void dataDirThrowsWhenUnconfigured() {
        TripTaleProperties props = new TripTaleProperties();
        props.setSettingsDir(tempDir.resolve("unconfigured-settings").toString());
        MarkdownStore unconfiguredStore = new MarkdownStore(new SettingsStore(props));
        assertThrows(StorageException.class, unconfiguredStore::dataDir);
    }

    @Test
    void saveAndLoadTripRoundTrip() {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "Summer ride");
        store.saveTrip(trip);

        Optional<Trip> loaded = store.loadTrip(ALPS_2025);
        assertTrue(loaded.isPresent());
        assertEquals("alps-2025", loaded.get().slug());
        assertEquals(2025, loaded.get().year());
        assertEquals("Alps 2025", loaded.get().name());
        assertEquals(LocalDate.of(2025, 7, 1), loaded.get().startDate());
        assertEquals("Summer ride", loaded.get().description());
    }

    @Test
    void loadTripReturnsEmptyForUnknownSlug() {
        assertTrue(store.loadTrip(new TripRef(2025, "nonexistent")).isEmpty());
    }

    @Test
    void saveTripWritesReadmeWithTripTypeAndNameAsHeading() throws Exception {
        store.saveTrip(new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "Summer ride"));

        Path readme = store.dataDir().resolve("2025").resolve("alps-2025").resolve("README.md");
        assertTrue(Files.exists(readme));
        String raw = Files.readString(readme);
        assertTrue(raw.contains("type: Trip"));
        assertFalse(raw.contains("name:"));
        assertTrue(raw.contains("# Alps 2025"));
        assertTrue(raw.endsWith("Summer ride"));
    }

    @Test
    void saveTripWritesStartDateAsSnakeCaseKey() throws Exception {
        store.saveTrip(new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, ""));

        Path readme = store.dataDir().resolve("2025").resolve("alps-2025").resolve("README.md");
        String raw = Files.readString(readme);
        assertTrue(raw.contains("start_date: 2025-07-01"));
        assertFalse(raw.contains("startDate"));
    }

    @Test
    void saveTripOmitsEndDateKeyWhenNull() throws Exception {
        store.saveTrip(new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, ""));

        Path readme = store.dataDir().resolve("2025").resolve("alps-2025").resolve("README.md");
        String raw = Files.readString(readme);
        assertFalse(raw.contains("end_date"));
    }

    @Test
    void saveAndLoadTripRoundTripsEndDate() {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1),
                LocalDate.of(2025, 7, 14), "Summer ride");
        store.saveTrip(trip);

        Optional<Trip> loaded = store.loadTrip(ALPS_2025);
        assertTrue(loaded.isPresent());
        assertEquals(LocalDate.of(2025, 7, 14), loaded.get().endDate());
    }

    @Test
    void tripExistsReflectsFileSystemState() {
        assertFalse(store.tripExists(ALPS_2025));
        store.saveTrip(new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, ""));
        assertTrue(store.tripExists(ALPS_2025));
    }

    @Test
    void listTripsReturnsTripsForYearSortedByStartDateDescending() {
        store.saveTrip(new Trip(2025, "bravo", "Bravo", LocalDate.of(2025, 1, 1), null, ""));
        store.saveTrip(new Trip(2025, "alpha", "Alpha", LocalDate.of(2025, 6, 1), null, ""));
        store.saveTrip(new Trip(2025, "charlie", "Charlie", LocalDate.of(2025, 3, 1), null, ""));

        List<Trip> trips = store.listTrips(2025);
        assertEquals(List.of("alpha", "charlie", "bravo"), trips.stream().map(Trip::slug).toList());
    }

    @Test
    void listTripsScopesToRequestedYear() {
        store.saveTrip(new Trip(2024, "iceland", "Iceland", LocalDate.of(2024, 6, 1), null, ""));
        store.saveTrip(new Trip(2025, "iceland", "Iceland Again", LocalDate.of(2025, 6, 1), null, ""));

        assertEquals(List.of("Iceland"), store.listTrips(2024).stream().map(Trip::name).toList());
        assertEquals(List.of("Iceland Again"), store.listTrips(2025).stream().map(Trip::name).toList());
    }

    @Test
    void listTripsReturnsEmptyListWhenNoTripsForYear() {
        assertTrue(store.listTrips(2025).isEmpty());
    }

    @Test
    void listYearsReturnsOnlyFourDigitDirectoriesDescending() throws Exception {
        store.saveTrip(new Trip(2024, "a", "A", LocalDate.of(2024, 1, 1), null, ""));
        store.saveTrip(new Trip(2026, "b", "B", LocalDate.of(2026, 1, 1), null, ""));
        Files.createDirectories(store.dataDir().resolve("not-a-year"));

        assertEquals(List.of(2026, 2024), store.listYears());
    }

    @Test
    void listYearsReturnsEmptyListWhenNoYearDirectories() {
        assertTrue(store.listYears().isEmpty());
    }

    @Test
    void entryFileNameContainsDateAndEnglishWeekday() {
        Path file = store.entryFile(ALPS_2025, LocalDate.of(2025, 7, 4));
        assertEquals("2025-07-04-Friday.md", file.getFileName().toString());
    }

    @Test
    void entryFileLivesDirectlyInTripFolderNotAnEntriesSubfolder() {
        Path file = store.entryFile(ALPS_2025, LocalDate.of(2025, 7, 4));
        assertEquals(store.tripDir(ALPS_2025), file.getParent());
    }

    @Test
    void tripDirIsNestedUnderYear() {
        Path dir = store.tripDir(ALPS_2025);
        assertEquals("alps-2025", dir.getFileName().toString());
        assertEquals("2025", dir.getParent().getFileName().toString());
    }

    @Test
    void listEntryDatesIgnoresReadme() {
        store.saveTrip(new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "desc"));
        store.saveEntry(ALPS_2025, DiaryEntry.builder(LocalDate.of(2025, 7, 4)).tales("x").build());

        assertEquals(List.of(LocalDate.of(2025, 7, 4)), store.listEntryDates(ALPS_2025));
    }

    @Test
    void saveAndLoadEntryRoundTrip() throws Exception {
        store.saveTrip(new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, ""));
        DiaryEntry entry = DiaryEntry.builder(LocalDate.of(2025, 7, 4))
                .distance(82.5)
                .altitudeMeters(1240.0)
                .title("Innsbruck → Brenner")
                .trackUrl("https://www.strava.com/activities/123")
                .tales("Hot day, lots of climbing.")
                .build();
        store.saveEntry(ALPS_2025, entry);

        String raw = Files.readString(store.entryFile(ALPS_2025, LocalDate.of(2025, 7, 4)));
        assertTrue(raw.contains("trackurl:"));
        assertFalse(raw.contains("route:"));
        assertTrue(raw.contains("# Innsbruck → Brenner\n\nHot day"), "body should start with H1 title");

        DiaryEntry loaded = store.loadEntry(ALPS_2025, LocalDate.of(2025, 7, 4));
        assertEquals(LocalDate.of(2025, 7, 4), loaded.date());
        assertEquals(82.5, loaded.distance());
        assertEquals(1240.0, loaded.altitudeMeters());
        assertEquals("Innsbruck → Brenner", loaded.title());
        assertEquals("https://www.strava.com/activities/123", loaded.trackUrl());
        assertTrue(loaded.tales().contains("Hot day"));
    }

    @Test
    void saveEntryWritesTaleTypeAndKeepsFrontmatterAlphabetical() throws Exception {
        store.saveTrip(new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, ""));
        store.saveEntry(ALPS_2025, DiaryEntry.builder(LocalDate.of(2025, 7, 4))
                .distance(82.5)
                .altitudeMeters(1240.0)
                .title("Innsbruck → Brenner")
                .trackUrl("https://www.strava.com/activities/123")
                .tales("Hot day.")
                .build());

        String raw = Files.readString(store.entryFile(ALPS_2025, LocalDate.of(2025, 7, 4)));
        assertTrue(raw.contains("type: Tale"));
        assertFalse(raw.contains("route:"));

        List<String> frontmatterKeys = raw.substring(raw.indexOf("---") + 3, raw.indexOf("---", 3))
                .lines()
                .filter(l -> !l.isBlank())
                .map(l -> l.split(":", 2)[0])
                .toList();
        List<String> sorted = frontmatterKeys.stream().sorted().toList();
        assertEquals(sorted, frontmatterKeys);
    }

    @Test
    void saveEntryDemotesH1LinesInTalesBodyOnRoundTrip() {
        store.saveTrip(new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, ""));
        store.saveEntry(ALPS_2025, DiaryEntry.builder(LocalDate.of(2025, 7, 4))
                .title("First part")
                .tales("# something\ntough climb\n\n## 2nd part\nmuch better")
                .build());

        DiaryEntry loaded = store.loadEntry(ALPS_2025, LocalDate.of(2025, 7, 4));
        assertEquals("First part", loaded.title());
        assertTrue(loaded.tales().lines().anyMatch(l -> l.equals("## something")),
                "H1 line in tales body should be demoted to H2");
        assertTrue(loaded.tales().lines().noneMatch(l -> l.equals("# something")),
                "original H1 line should no longer be present as-is");
        assertTrue(loaded.tales().lines().anyMatch(l -> l.equals("## 2nd part")),
                "existing H2 line should be untouched");
    }

    @Test
    void loadEntryWithoutH1LineDefaultsToUntitled() throws Exception {
        store.saveTrip(new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, ""));
        Path file = store.entryFile(ALPS_2025, LocalDate.of(2025, 7, 4));
        Files.writeString(file, "---\ndate: 2025-07-04\n---\n\nJust some text, no heading.");

        DiaryEntry loaded = store.loadEntry(ALPS_2025, LocalDate.of(2025, 7, 4));
        assertEquals(DiaryEntry.DEFAULT_TITLE, loaded.title());
        assertEquals("Just some text, no heading.", loaded.tales());
    }

    @Test
    void saveEntryWritesBelongsToPointingAtTripReadme() throws Exception {
        store.saveTrip(new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, ""));
        store.saveEntry(ALPS_2025, DiaryEntry.builder(LocalDate.of(2025, 7, 4))
                .tales("Hot day.")
                .build());

        String raw = Files.readString(store.entryFile(ALPS_2025, LocalDate.of(2025, 7, 4)));
        assertTrue(raw.contains("belongs_to: \"[[2025/alps-2025/README]]\""));
    }

    @Test
    void saveEntryOmitsBlankTrackUrlFromFrontmatter() throws Exception {
        store.saveTrip(new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, ""));
        store.saveEntry(ALPS_2025, DiaryEntry.builder(LocalDate.of(2025, 7, 4))
                .trackUrl("   ")
                .tales("No track.")
                .build());

        String raw = Files.readString(store.entryFile(ALPS_2025, LocalDate.of(2025, 7, 4)));
        assertFalse(raw.contains("trackurl"));
        assertNull(store.loadEntry(ALPS_2025, LocalDate.of(2025, 7, 4)).trackUrl());
    }

    @Test
    void saveEntryOmitsNullDistanceAndAltitudeFromFrontmatter() throws Exception {
        store.saveTrip(new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, ""));
        DiaryEntry entry = DiaryEntry.builder(LocalDate.of(2025, 7, 4))
                .tales("Rest day.")
                .build();
        store.saveEntry(ALPS_2025, entry);

        String raw = Files.readString(store.entryFile(ALPS_2025, LocalDate.of(2025, 7, 4)));
        assertFalse(raw.contains("distance"));
        assertFalse(raw.contains("altitude"));
        assertTrue(raw.contains("Rest day."));

        DiaryEntry loaded = store.loadEntry(ALPS_2025, LocalDate.of(2025, 7, 4));
        assertNull(loaded.distance());
        assertNull(loaded.altitudeMeters());
    }

    @Test
    void loadEntryReturnsEmptyWhenFileMissing() {
        DiaryEntry empty = store.loadEntry(ALPS_2025, LocalDate.of(2025, 7, 4));
        assertEquals(LocalDate.of(2025, 7, 4), empty.date());
        assertNull(empty.distance());
        assertNull(empty.altitudeMeters());
        assertNotNull(empty.tales());
        assertTrue(empty.tales().isEmpty());
    }

    @Test
    void entryExistsReflectsFileSystemState() {
        store.saveTrip(new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, ""));
        assertFalse(store.entryExists(ALPS_2025, LocalDate.of(2025, 7, 4)));
        store.saveEntry(ALPS_2025, DiaryEntry.builder(LocalDate.of(2025, 7, 4)).tales("x").build());
        assertTrue(store.entryExists(ALPS_2025, LocalDate.of(2025, 7, 4)));
    }

    @Test
    void listEntryDatesReturnsSortedDates() {
        store.saveTrip(new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, ""));
        store.saveEntry(ALPS_2025, DiaryEntry.builder(LocalDate.of(2025, 7, 3)).build());
        store.saveEntry(ALPS_2025, DiaryEntry.builder(LocalDate.of(2025, 7, 1)).build());
        store.saveEntry(ALPS_2025, DiaryEntry.builder(LocalDate.of(2025, 7, 2)).build());

        List<LocalDate> dates = store.listEntryDates(ALPS_2025);
        assertEquals(List.of(
                LocalDate.of(2025, 7, 1),
                LocalDate.of(2025, 7, 2),
                LocalDate.of(2025, 7, 3)
        ), dates);
    }

    @Test
    void listEntryDatesReturnsEmptyForTripWithoutEntries() {
        store.saveTrip(new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, ""));
        assertTrue(store.listEntryDates(ALPS_2025).isEmpty());
    }

    // -------------------------------------------------------------------------
    // Repo-local internal state (.state.yml)
    // -------------------------------------------------------------------------

    @Test
    void saveAndLoadLastTripPathRoundTrip() {
        store.saveLastTripPath(ALPS_2025);
        Optional<TripRef> loaded = store.loadLastTripPath();
        assertTrue(loaded.isPresent());
        assertEquals(ALPS_2025, loaded.get());
    }

    @Test
    void loadLastTripPathReturnsEmptyWhenStateFileMissing() {
        assertTrue(store.loadLastTripPath().isEmpty());
    }

    @Test
    void saveLastTripPathOverwritesPreviousValue() {
        store.saveLastTripPath(new TripRef(2024, "trip-one"));
        store.saveLastTripPath(new TripRef(2025, "trip-two"));
        assertEquals(new TripRef(2025, "trip-two"), store.loadLastTripPath().orElseThrow());
    }

    @Test
    void lastTripPathIsPersistedInStateYmlFile() {
        store.saveLastTripPath(ALPS_2025);
        Path stateFile = store.dataDir().resolve(".state.yml");
        assertTrue(Files.exists(stateFile));
    }

    @Test
    void loadLastTripPathReturnsEmptyForMalformedValue() {
        store.saveState("lastTripPath", "not-a-valid-path");
        assertTrue(store.loadLastTripPath().isEmpty());
    }
}
