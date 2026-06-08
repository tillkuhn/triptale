package net.timafe.triptale.export;

import net.timafe.triptale.config.TripTaleProperties;
import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.storage.MarkdownStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiaryExporterTest {

    @TempDir
    Path tempDir;

    private MarkdownStore store;
    private DiaryExporter exporter;

    @BeforeEach
    void setUp() {
        TripTaleProperties props = new TripTaleProperties();
        props.setDataDir(tempDir.toString());
        store = new MarkdownStore(props);
        exporter = new DiaryExporter(store);
    }

    @Test
    void exportsHeaderTotalsAndEntries() {
        Trip trip = new Trip("alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), "Summer ride");
        store.saveTrip(trip);
        store.saveEntry("alps-2025", DiaryEntry.builder(LocalDate.of(2025, 7, 1))
                .distance(50.0).altitudeMeters(800.0).route("A → B").tales("Day one").build());
        store.saveEntry("alps-2025", DiaryEntry.builder(LocalDate.of(2025, 7, 2))
                .distance(32.5).altitudeMeters(400.0).route("B → C").tales("Day two").build());

        String out = exporter.exportTrip(trip);

        assertTrue(out.startsWith("# Alps 2025"), "should start with trip name heading");
        assertTrue(out.contains("Summer ride"));
        assertTrue(out.contains("2025-07-01 → 2025-07-02 (2 days, 2 entries)"));
        assertTrue(out.contains("Distance: 82.5 km"));
        assertTrue(out.contains("Altitude: 1200 m"));
        assertTrue(out.contains("## 2025-07-01 Tuesday Day 1: A → B"));
        assertTrue(out.contains("## 2025-07-02 Wednesday Day 2: B → C"));
        assertTrue(out.contains("Distance covered: 50.0 km"));
        assertTrue(out.contains("Altitude climbed: 800 m"));
        assertTrue(out.contains("Day one"));
        assertTrue(out.contains("Day two"));
    }

    @Test
    void defaultRouteIsOmittedFromHeading() {
        Trip trip = new Trip("alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), "");
        store.saveTrip(trip);
        store.saveEntry("alps-2025", DiaryEntry.builder(LocalDate.of(2025, 7, 1))
                .route(DiaryEntry.DEFAULT_ROUTE).tales("hi").build());

        String out = exporter.exportTrip(trip);

        assertTrue(out.contains("## 2025-07-01 Tuesday Day 1"));
        assertFalse(out.contains(DiaryEntry.DEFAULT_ROUTE), "default route placeholder should not leak into export");
    }

    @Test
    void missingValuesRenderedAsEmDash() {
        Trip trip = new Trip("future", "Future Trip", null, "");
        store.saveTrip(trip);

        String out = exporter.exportTrip(trip);

        assertTrue(out.contains("— → —"), "missing start/end date should render as em-dash");
        assertTrue(out.contains("0 entries"));
    }

    @Test
    void distanceAndAltitudeStatsOmittedWhenAbsent() {
        Trip trip = new Trip("alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), "");
        store.saveTrip(trip);
        store.saveEntry("alps-2025", DiaryEntry.builder(LocalDate.of(2025, 7, 1)).tales("Rest day.").build());

        String out = exporter.exportTrip(trip);

        assertTrue(out.contains("Rest day."));
        assertFalse(out.contains("Distance covered:"));
        assertFalse(out.contains("Altitude climbed:"));
    }

    @Test
    void outputEndsWithSingleTrailingNewline() {
        Trip trip = new Trip("alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), "");
        store.saveTrip(trip);
        store.saveEntry("alps-2025", DiaryEntry.builder(LocalDate.of(2025, 7, 1)).tales("hi").build());

        String out = exporter.exportTrip(trip);
        assertTrue(out.endsWith("\n"));
        assertFalse(out.endsWith("\n\n"), "should not have double trailing newline");
    }

    @Test
    void daysCountIsInclusiveOfStartAndEnd() {
        Trip trip = new Trip("alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), "");
        store.saveTrip(trip);
        store.saveEntry("alps-2025", DiaryEntry.builder(LocalDate.of(2025, 7, 1)).tales(".").build());
        store.saveEntry("alps-2025", DiaryEntry.builder(LocalDate.of(2025, 7, 5)).tales(".").build());

        String out = exporter.exportTrip(trip);
        assertTrue(out.contains("(5 days, 2 entries)"),
                "day count should span first to last inclusive; got:\n" + out);
    }

    @Test
    void exportedTripCanBeCalledTwiceWithSameOutput() {
        Trip trip = new Trip("alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), "x");
        store.saveTrip(trip);
        store.saveEntry("alps-2025", DiaryEntry.builder(LocalDate.of(2025, 7, 1)).distance(10.0).tales("a").build());

        assertEquals(exporter.exportTrip(trip), exporter.exportTrip(trip));
    }
}
