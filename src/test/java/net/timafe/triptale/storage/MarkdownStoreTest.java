package net.timafe.triptale.storage;

import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.config.TripTaleProperties;
import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.domain.Trip;
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
    void dataDirCreatesRootAndTripsSubdir() {
        Path root = store.dataDir();
        assertTrue(Files.isDirectory(root));
        assertTrue(Files.isDirectory(root.resolve("trips")));
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
        Trip trip = new Trip("alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), "Summer ride");
        store.saveTrip(trip);

        Optional<Trip> loaded = store.loadTrip("alps-2025");
        assertTrue(loaded.isPresent());
        assertEquals("alps-2025", loaded.get().slug());
        assertEquals("Alps 2025", loaded.get().name());
        assertEquals(LocalDate.of(2025, 7, 1), loaded.get().startDate());
        assertEquals("Summer ride", loaded.get().description());
    }

    @Test
    void loadTripReturnsEmptyForUnknownSlug() {
        assertTrue(store.loadTrip("nonexistent").isEmpty());
    }

    @Test
    void saveTripWritesReadmeWithTripTypeAndNameAsHeading() throws Exception {
        store.saveTrip(new Trip("alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), "Summer ride"));

        Path readme = store.dataDir().resolve("trips").resolve("alps-2025").resolve("README.md");
        assertTrue(Files.exists(readme));
        String raw = Files.readString(readme);
        assertTrue(raw.contains("type: Trip"));
        assertFalse(raw.contains("name:"));
        assertTrue(raw.contains("# Alps 2025"));
        assertTrue(raw.endsWith("Summer ride"));
    }

    @Test
    void listTripsReturnsAllSavedTripsSortedBySlug() {
        store.saveTrip(new Trip("bravo", "Bravo", LocalDate.of(2025, 1, 1), ""));
        store.saveTrip(new Trip("alpha", "Alpha", LocalDate.of(2025, 1, 2), ""));
        store.saveTrip(new Trip("charlie", "Charlie", LocalDate.of(2025, 1, 3), ""));

        List<Trip> trips = store.listTrips();
        assertEquals(List.of("alpha", "bravo", "charlie"), trips.stream().map(Trip::slug).toList());
    }

    @Test
    void listTripsReturnsEmptyListWhenNoTrips() {
        assertTrue(store.listTrips().isEmpty());
    }

    @Test
    void entryFileNameContainsDateAndEnglishWeekday() {
        Path file = store.entryFile("alps-2025", LocalDate.of(2025, 7, 4));
        assertEquals("2025-07-04-Friday.md", file.getFileName().toString());
    }

    @Test
    void entryFileLivesDirectlyInTripFolderNotAnEntriesSubfolder() {
        Path file = store.entryFile("alps-2025", LocalDate.of(2025, 7, 4));
        assertEquals(store.tripDir("alps-2025"), file.getParent());
    }

    @Test
    void listEntryDatesIgnoresReadme() {
        store.saveTrip(new Trip("alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), "desc"));
        store.saveEntry("alps-2025", DiaryEntry.builder(LocalDate.of(2025, 7, 4)).tales("x").build());

        assertEquals(List.of(LocalDate.of(2025, 7, 4)), store.listEntryDates("alps-2025"));
    }

    @Test
    void saveAndLoadEntryRoundTrip() throws Exception {
        store.saveTrip(new Trip("alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), ""));
        DiaryEntry entry = DiaryEntry.builder(LocalDate.of(2025, 7, 4))
                .distance(82.5)
                .altitudeMeters(1240.0)
                .route("Innsbruck → Brenner")
                .trackUrl("https://www.strava.com/activities/123")
                .tales("Hot day, lots of climbing.")
                .build();
        store.saveEntry("alps-2025", entry);

        String raw = Files.readString(store.entryFile("alps-2025", LocalDate.of(2025, 7, 4)));
        assertTrue(raw.contains("trackurl:"));

        DiaryEntry loaded = store.loadEntry("alps-2025", LocalDate.of(2025, 7, 4));
        assertEquals(LocalDate.of(2025, 7, 4), loaded.date());
        assertEquals(82.5, loaded.distance());
        assertEquals(1240.0, loaded.altitudeMeters());
        assertEquals("Innsbruck → Brenner", loaded.route());
        assertEquals("https://www.strava.com/activities/123", loaded.trackUrl());
        assertTrue(loaded.tales().contains("Hot day"));
    }

    @Test
    void saveEntryWritesTaleTypeAndKeepsFrontmatterAlphabetical() throws Exception {
        store.saveTrip(new Trip("alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), ""));
        store.saveEntry("alps-2025", DiaryEntry.builder(LocalDate.of(2025, 7, 4))
                .distance(82.5)
                .altitudeMeters(1240.0)
                .route("Innsbruck → Brenner")
                .trackUrl("https://www.strava.com/activities/123")
                .tales("Hot day.")
                .build());

        String raw = Files.readString(store.entryFile("alps-2025", LocalDate.of(2025, 7, 4)));
        assertTrue(raw.contains("type: Tale"));

        List<String> frontmatterKeys = raw.substring(raw.indexOf("---") + 3, raw.indexOf("---", 3))
                .lines()
                .filter(l -> !l.isBlank())
                .map(l -> l.split(":", 2)[0])
                .toList();
        List<String> sorted = frontmatterKeys.stream().sorted().toList();
        assertEquals(sorted, frontmatterKeys);
    }

    @Test
    void saveEntryOmitsBlankTrackUrlFromFrontmatter() throws Exception {
        store.saveTrip(new Trip("alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), ""));
        store.saveEntry("alps-2025", DiaryEntry.builder(LocalDate.of(2025, 7, 4))
                .trackUrl("   ")
                .tales("No track.")
                .build());

        String raw = Files.readString(store.entryFile("alps-2025", LocalDate.of(2025, 7, 4)));
        assertFalse(raw.contains("trackurl"));
        assertNull(store.loadEntry("alps-2025", LocalDate.of(2025, 7, 4)).trackUrl());
    }

    @Test
    void saveEntryOmitsNullDistanceAndAltitudeFromFrontmatter() throws Exception {
        store.saveTrip(new Trip("alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), ""));
        DiaryEntry entry = DiaryEntry.builder(LocalDate.of(2025, 7, 4))
                .tales("Rest day.")
                .build();
        store.saveEntry("alps-2025", entry);

        String raw = Files.readString(store.entryFile("alps-2025", LocalDate.of(2025, 7, 4)));
        assertFalse(raw.contains("distance"));
        assertFalse(raw.contains("altitude"));
        assertTrue(raw.contains("Rest day."));

        DiaryEntry loaded = store.loadEntry("alps-2025", LocalDate.of(2025, 7, 4));
        assertNull(loaded.distance());
        assertNull(loaded.altitudeMeters());
    }

    @Test
    void loadEntryReturnsEmptyWhenFileMissing() {
        DiaryEntry empty = store.loadEntry("alps-2025", LocalDate.of(2025, 7, 4));
        assertEquals(LocalDate.of(2025, 7, 4), empty.date());
        assertNull(empty.distance());
        assertNull(empty.altitudeMeters());
        assertNotNull(empty.tales());
        assertTrue(empty.tales().isEmpty());
    }

    @Test
    void entryExistsReflectsFileSystemState() {
        store.saveTrip(new Trip("alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), ""));
        assertFalse(store.entryExists("alps-2025", LocalDate.of(2025, 7, 4)));
        store.saveEntry("alps-2025", DiaryEntry.builder(LocalDate.of(2025, 7, 4)).tales("x").build());
        assertTrue(store.entryExists("alps-2025", LocalDate.of(2025, 7, 4)));
    }

    @Test
    void listEntryDatesReturnsSortedDates() {
        store.saveTrip(new Trip("alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), ""));
        store.saveEntry("alps-2025", DiaryEntry.builder(LocalDate.of(2025, 7, 3)).build());
        store.saveEntry("alps-2025", DiaryEntry.builder(LocalDate.of(2025, 7, 1)).build());
        store.saveEntry("alps-2025", DiaryEntry.builder(LocalDate.of(2025, 7, 2)).build());

        List<LocalDate> dates = store.listEntryDates("alps-2025");
        assertEquals(List.of(
                LocalDate.of(2025, 7, 1),
                LocalDate.of(2025, 7, 2),
                LocalDate.of(2025, 7, 3)
        ), dates);
    }

    @Test
    void listEntryDatesReturnsEmptyForTripWithoutEntries() {
        store.saveTrip(new Trip("alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), ""));
        assertTrue(store.listEntryDates("alps-2025").isEmpty());
    }

    // -------------------------------------------------------------------------
    // Repo-local internal state (.state.yml)
    // -------------------------------------------------------------------------

    @Test
    void saveAndLoadLastTripSlugRoundTrip() {
        store.saveLastTripSlug("alps-2025");
        Optional<String> loaded = store.loadLastTripSlug();
        assertTrue(loaded.isPresent());
        assertEquals("alps-2025", loaded.get());
    }

    @Test
    void loadLastTripSlugReturnsEmptyWhenStateFileMissing() {
        assertTrue(store.loadLastTripSlug().isEmpty());
    }

    @Test
    void saveLastTripSlugOverwritesPreviousValue() {
        store.saveLastTripSlug("trip-one");
        store.saveLastTripSlug("trip-two");
        assertEquals("trip-two", store.loadLastTripSlug().orElseThrow());
    }

    @Test
    void lastTripSlugIsPersistedInStateYmlFile() {
        store.saveLastTripSlug("alps-2025");
        Path stateFile = store.dataDir().resolve(".state.yml");
        assertTrue(Files.exists(stateFile));
    }
}
