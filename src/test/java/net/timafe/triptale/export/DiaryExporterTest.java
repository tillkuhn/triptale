package net.timafe.triptale.export;

import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.config.TripTaleProperties;
import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.attachments.AttachmentsDir;
import net.timafe.triptale.impressions.ImpressionSource;
import net.timafe.triptale.impressions.ImpressionsService;
import net.timafe.triptale.storage.ImpressionsResolver;
import net.timafe.triptale.storage.PathPatternResolver;
import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.storage.SettingsStore;
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
    private SettingsStore settingsStore;
    private DiaryExporter exporter;
    private AttachmentsDir attachmentsDir;

    @BeforeEach
    void setUp() {
        Path settingsDir = tempDir.resolve("settings");
        Path dataDir = tempDir.resolve("data");
        TripTaleProperties props = new TripTaleProperties();
        props.setSettingsDir(settingsDir.toString());
        settingsStore = new SettingsStore(props);
        AppSettings settings = new AppSettings();
        settings.setDataDir(dataDir.toString());
        settingsStore.save(settings);
        store = new MarkdownStore(settingsStore);
        attachmentsDir = new AttachmentsDir(store);
        ImpressionsService impressions = new ImpressionsService(
                new ImpressionsResolver(new PathPatternResolver()), attachmentsDir, settingsStore);
        exporter = new DiaryExporter(store, impressions, settingsStore);
    }

    private void setImpressionsFilePattern(String pattern) {
        AppSettings settings = settingsStore.load();
        settings.setImpressionsFilePattern(pattern);
        settingsStore.save(settings);
    }

    private void setImpressionsFaveFilter(String filter) {
        AppSettings settings = settingsStore.load();
        settings.setImpressionsFaveFilter(filter);
        settingsStore.save(settings);
    }

    private void setImpressionsGridColumns(int columns) {
        AppSettings settings = settingsStore.load();
        settings.setImpressionsGridColumns(columns);
        settingsStore.save(settings);
    }

    @Test
    void exportsHeaderTotalsAndEntries() {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "Summer ride");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1))
                .distance(50.0).altitudeMeters(800.0).title("A → B").tales("Day one").build());
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 2))
                .distance(32.5).altitudeMeters(400.0).title("B → C").tales("Day two").build());

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
    void defaultTitleIsIncludedInHeading() {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1))
                .title(DiaryEntry.DEFAULT_TITLE).tales("hi").build());

        String out = exporter.exportTrip(trip);

        assertTrue(out.contains("## 2025-07-01 Tuesday Day 1: Untitled"),
                "with no blank-check guard, the default title placeholder should appear in the heading");
    }

    @Test
    void missingValuesRenderedAsEmDash() {
        Trip trip = new Trip(2025, "future", "Future Trip", null, null, "");
        store.saveTrip(trip);

        String out = exporter.exportTrip(trip);

        assertTrue(out.contains("— → —"), "missing start/end date should render as em-dash");
        assertTrue(out.contains("0 entries"));
    }

    @Test
    void distanceAndAltitudeStatsOmittedWhenAbsent() {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1)).tales("Rest day.").build());

        String out = exporter.exportTrip(trip);

        assertTrue(out.contains("Rest day."));
        assertFalse(out.contains("Distance covered:"));
        assertFalse(out.contains("Altitude climbed:"));
        assertFalse(out.contains("Start:"));
    }

    @Test
    void includesStartPointAsDdmWhenBothCoordinatesSet() {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1))
                .startLat(51.488).startLon(-0.013).tales("Started in London.").build());

        String out = exporter.exportTrip(trip);

        assertTrue(out.contains("Start: 51°29′N 0°01′W"));
    }

    @Test
    void omitsStartPointWhenOnlyOneCoordinateSet() {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1))
                .startLat(51.488).tales("Partial coordinates.").build());

        String out = exporter.exportTrip(trip);

        assertFalse(out.contains("Start:"));
    }

    @Test
    void outputEndsWithSingleTrailingNewline() {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1)).tales("hi").build());

        String out = exporter.exportTrip(trip);
        assertTrue(out.endsWith("\n"));
        assertFalse(out.endsWith("\n\n"), "should not have double trailing newline");
    }

    @Test
    void daysCountIsInclusiveOfStartAndEnd() {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1)).tales(".").build());
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 5)).tales(".").build());

        String out = exporter.exportTrip(trip);
        assertTrue(out.contains("(5 days, 2 entries)"),
                "day count should span first to last inclusive; got:\n" + out);
    }

    @Test
    void exportedTripCanBeCalledTwiceWithSameOutput() {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "x");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1)).distance(10.0).tales("a").build());

        assertEquals(exporter.exportTrip(trip), exporter.exportTrip(trip));
    }

    @Test
    void exportTripAsHtmlRendersHeadingsAndParagraphs() {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "Summer ride");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1))
                .distance(50.0).altitudeMeters(800.0).title("A → B").tales("Day **one** was *great*.").build());

        String html = exporter.exportTripAsHtml(trip);

        assertTrue(html.contains("<!DOCTYPE html>"));
        assertTrue(html.contains("<title>Alps 2025</title>"));
        assertTrue(html.contains("<h1>Alps 2025</h1>"));
        assertTrue(html.contains("<h2>2025-07-01 Tuesday Day 1: A → B</h2>"));
        assertTrue(html.contains("<strong>one</strong>"));
        assertTrue(html.contains("<em>great</em>"));
        assertTrue(html.contains("<p>"));
    }

    @Test
    void exportTripAsHtmlEscapesTripNameInTitle() {
        Trip trip = new Trip(2025, "weird", "A & B <Trip>", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1)).tales("hi").build());

        String html = exporter.exportTripAsHtml(trip);

        assertTrue(html.contains("<title>A &amp; B &lt;Trip&gt;</title>"));
    }

    @Test
    void exportTripAsHtmlWithoutImpressionsFlagOmitsImageMarkersAndGrid() {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1)).tales("hi").build());
        setImpressionsFilePattern(tempDir.toString() + "/${DATE}*.jpg");

        String html = exporter.exportTripAsHtml(trip, null, null, null, false);

        assertFalse(html.contains("IMPRESSIONS"));
        assertFalse(html.contains("<table"));
    }

    @Test
    void exportTripAsHtmlWithImpressionsFlagInjectsImageGrid() throws java.io.IOException {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1)).tales("hi").build());
        java.nio.file.Files.createFile(tempDir.resolve("20250701_one.jpg"));
        java.nio.file.Files.createFile(tempDir.resolve("20250701_two.jpg"));
        setImpressionsFilePattern(tempDir.toString() + "/${DATE}*.jpg");
        setImpressionsGridColumns(2);

        String html = exporter.exportTripAsHtml(trip, null, null, ImpressionSource.PHOTO_LIBRARY, false);

        assertFalse(html.contains("IMPRESSIONS"), "marker should be replaced");
        assertTrue(html.contains("<div class=\"impressions\" style=\"column-count: 2;\">"));
        assertTrue(html.contains("20250701_one.jpg"));
        assertTrue(html.contains("20250701_two.jpg"));
    }

    @Test
    void exportTripAsHtmlWithFavesOnlyAppliesFaveFilter() throws java.io.IOException {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1)).tales("hi").build());
        java.nio.file.Files.createFile(tempDir.resolve("20250701_all.jpg"));
        java.nio.file.Files.createFile(tempDir.resolve("20250701_fave+.jpg"));
        setImpressionsFilePattern(tempDir.toString() + "/${DATE}*.jpg");
        setImpressionsFaveFilter("*+.*");

        String html = exporter.exportTripAsHtml(trip, null, null, ImpressionSource.PHOTO_LIBRARY, true);

        assertTrue(html.contains("<div class=\"impressions\""));
        assertTrue(html.contains("20250701_fave+.jpg"));
        assertFalse(html.contains("20250701_all.jpg"));
    }

    @Test
    void exportTripAsHtmlFromAttachmentsUsesDayFolderAndBaseFilter() throws java.io.IOException {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        LocalDate day = LocalDate.of(2025, 7, 1);
        store.saveEntry(trip.ref(), DiaryEntry.builder(day).tales("hi").build());
        java.nio.file.Path dir = attachmentsDir.ensureDayDir(trip.ref(), day);
        java.nio.file.Files.createFile(dir.resolve("summit.JPG"));
        java.nio.file.Files.createFile(dir.resolve("track.gpx"));

        String html = exporter.exportTripAsHtml(trip, null, null, ImpressionSource.TRIP_ATTACHMENTS, false);

        assertTrue(html.contains("summit.JPG"));
        assertFalse(html.contains("track.gpx"));
    }

    @Test
    void exportTripRangeCoversOnlySelectedEntriesAndTotals() {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1)).title("One").distance(10.0).tales("a").build());
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 2)).title("Two").distance(20.0).tales("b").build());
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 3)).title("Three").distance(40.0).tales("c").build());

        String out = exporter.exportTrip(trip, LocalDate.of(2025, 7, 2), LocalDate.of(2025, 7, 2));

        assertTrue(out.contains("Day 2: Two"), out);
        assertFalse(out.contains("One"), out);
        assertFalse(out.contains("Three"), out);
        assertTrue(out.contains("20.0"), out);
        assertFalse(out.contains("70.0"), out);
    }

    @Test
    void exportTripAsHtmlGracefullyOmitsGridWhenPatternNotConfigured() {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1)).tales("hi").build());
        // No impressionsFilePattern or impressionsFaveFilter configured at all.

        String html = exporter.exportTripAsHtml(trip, null, null, ImpressionSource.PHOTO_LIBRARY, false);

        assertFalse(html.contains("IMPRESSIONS"));
        assertFalse(html.contains("<table"));

        String favesHtml = exporter.exportTripAsHtml(trip, null, null, ImpressionSource.PHOTO_LIBRARY, true);
        assertFalse(favesHtml.contains("IMPRESSIONS"));
        assertFalse(favesHtml.contains("<table"));
    }


    @Test
    void exportShiftsTaleSubheadingsSoTheyDontCollideWithEntryHeading() {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1))
                .title("A → B").tales("Intro text.\n\n## Subheading\n\nMore detail.").build());

        String out = exporter.exportTrip(trip);

        assertTrue(out.contains("## 2025-07-01 Tuesday Day 1: A → B"),
                "entry heading should remain at H2");
        assertTrue(out.lines().anyMatch(l -> l.equals("### Subheading")),
                "tale subheading should be shifted to H3 to avoid colliding with the entry heading");
        assertFalse(out.lines().anyMatch(l -> l.equals("## Subheading")),
                "unshifted H2 subheading would collide with the entry's own H2 heading");

        long h2Count = out.lines().filter(l -> l.startsWith("## ")).count();
        assertEquals(1, h2Count, "only the entry's own heading should be at H2 level; got:\n" + out);
    }

    @Test
    void exportTripPlainMarkdownNeverIncludesImpressions() throws java.io.IOException {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1)).tales("hi").build());
        java.nio.file.Files.createFile(tempDir.resolve("20250701_one.jpg"));
        setImpressionsFilePattern(tempDir.toString() + "/${DATE}*.jpg");

        String markdown = exporter.exportTrip(trip);

        assertFalse(markdown.contains("IMPRESSIONS"));
        assertFalse(markdown.contains(".jpg"));
    }
}
