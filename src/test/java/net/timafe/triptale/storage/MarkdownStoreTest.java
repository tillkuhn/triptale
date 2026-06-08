package net.timafe.triptale.storage;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownStoreTest {

    @TempDir
    Path tempDir;

    private MarkdownStore store;

    @BeforeEach
    void setUp() {
        TripTaleProperties props = new TripTaleProperties();
        props.setDataDir(tempDir.toString());
        store = new MarkdownStore(props);
    }

    @Test
    void dataDirCreatesRootAndTripsSubdir() {
        Path root = store.dataDir();
        assertTrue(Files.isDirectory(root));
        assertTrue(Files.isDirectory(root.resolve("trips")));
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
        assertEquals("2025-07-04_Friday.md", file.getFileName().toString());
    }

    @Test
    void saveAndLoadEntryRoundTrip() {
        store.saveTrip(new Trip("alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), ""));
        DiaryEntry entry = DiaryEntry.builder(LocalDate.of(2025, 7, 4))
                .distance(82.5)
                .altitudeMeters(1240.0)
                .route("Innsbruck → Brenner")
                .tales("Hot day, lots of climbing.")
                .build();
        store.saveEntry("alps-2025", entry);

        DiaryEntry loaded = store.loadEntry("alps-2025", LocalDate.of(2025, 7, 4));
        assertEquals(LocalDate.of(2025, 7, 4), loaded.date());
        assertEquals(82.5, loaded.distance());
        assertEquals(1240.0, loaded.altitudeMeters());
        assertEquals("Innsbruck → Brenner", loaded.route());
        assertTrue(loaded.tales().contains("Hot day"));
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
}
