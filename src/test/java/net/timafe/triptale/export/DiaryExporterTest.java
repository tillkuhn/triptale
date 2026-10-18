package net.timafe.triptale.export;

import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.config.TripTaleProperties;
import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.domain.Link;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.domain.Weather;
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
        assertTrue(out.contains("July 1st 2025 → July 2nd 2025 (2 days, 2 entries)"), out);
        assertTrue(out.contains("Distance: 82.5 km"));
        assertTrue(out.contains("Altitude: 1200 m"));
        assertTrue(out.contains("## A → B\n\n*Tuesday, July 1st 2025 · Day 1 · 📏 50.0 km, 800 m climbed*"), out);
        assertTrue(out.contains("## B → C\n\n*Wednesday, July 2nd 2025 · Day 2 · 📏 32.5 km, 400 m climbed*"), out);
        assertTrue(out.contains("Day one"));
        assertTrue(out.contains("Day two"));
    }

    @Test
    void defaultTitleFallsBackToDay() {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1))
                .title(DiaryEntry.DEFAULT_TITLE).tales("hi").build());

        String out = exporter.exportTrip(trip);

        assertTrue(out.contains("## Day 1\n"), out);
        assertFalse(out.contains("Untitled"), out);
    }

    @Test
    void defaultTitleWithoutTripStartFallsBackToDate() {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", null, null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1)).tales("hi").build());

        String out = exporter.exportTrip(trip);

        assertTrue(out.contains("## Tuesday, July 1st 2025\n\n*Tuesday, July 1st 2025*"), out);
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
        // Only the date is left
        assertTrue(out.contains("\n*Tuesday, July 1st 2025 · Day 1*\n"), out);
    }

    @Test
    void zeroDistanceCountsAsAbsentAndActivityShowsItsName() {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1))
                .activity("sightseeing").distance(0.0).altitudeMeters(0.0).tales("Castle.").build());

        String out = exporter.exportTrip(trip);

        assertTrue(out.contains("· Day 1 · 🏰 Sightseeing*"), out);
        assertFalse(out.contains(" km,"), out);
    }

    @Test
    void activityEmojiLeadsDistanceAndClimb() {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1))
                .activity("cycling").altitudeMeters(350.0).tales("Hills.").build());

        String out = exporter.exportTrip(trip);

        assertTrue(out.contains("· Day 1 · 🚴 350 m climbed*"), out);
    }

    @Test
    void includesStartPointAsDdmWhenBothCoordinatesSet() {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1))
                .startLat(51.488).startLon(-0.013).tales("Started in London.").build());

        String out = exporter.exportTrip(trip);

        assertTrue(out.contains("· 🧭 51°29′N 0°01′W*"), out);
    }

    @Test
    void stopPointFollowsStartPointWithArrow() {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1))
                .startLat(51.488).startLon(-0.013).stopLat(50.1).stopLon(8.67).tales("Far.").build());

        String out = exporter.exportTrip(trip);

        assertTrue(out.contains("· 🧭 51°29′N 0°01′W → 50°06′N 8°40′E*"), out);
    }

    @Test
    void omitsStartPointWhenOnlyOneCoordinateSet() {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1))
                .startLat(51.488).tales("Partial coordinates.").build());

        String out = exporter.exportTrip(trip);

        assertFalse(out.contains("🧭"), out);
    }

    @Test
    void linksListedAtEndOfEntryAlphabetically() {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1)).tales("Long day.")
                .link("sleep", Link.parse("https://nice.hotel.com/"))
                .link("eat", Link.of("https://mucfood.com/", "Muc Food"))
                .link("track", Link.parse("https://en.wikipedia.org/wiki/Bonn_(city)"))
                .link("tour", Link.parse("supertour"))
                .build());

        String out = exporter.exportTrip(trip);

        assertTrue(out.contains("""
                Long day.

                * 🍲 Eat at: [Muc Food](https://mucfood.com/)
                * 🛏 Sleep at: [nice.hotel.com](https://nice.hotel.com/)
                * 🔗 Tour: supertour
                * 🗺 Track tour: [en.wikipedia.org](https://en.wikipedia.org/wiki/Bonn_(city))"""), out);
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
        assertTrue(html.contains("<html lang=\"en\">"));
        assertTrue(html.contains("<title>Alps 2025</title>"));
        assertTrue(html.contains("<h1>Alps 2025</h1>"));
        assertTrue(html.contains("<h2>A → B</h2>"), html);
        assertTrue(html.contains("<strong>one</strong>"));
        assertTrue(html.contains("<em>great</em>"));
        assertTrue(html.contains("<p>"));
    }

    @Test
    void placeNamesReplaceCoordinatesInTheByline() {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1))
                .startLat(51.488).startLon(-0.013).startPlace("London, GB")
                .stopLat(50.1).stopLon(8.67).tales("Far.").build());

        String out = exporter.exportTrip(trip);

        // A stop without a place keeps its coordinates
        assertTrue(out.contains("· 🧭 London, GB → 50°06′N 8°40′E*"), out);
        assertTrue(out.strip().endsWith("*Place names © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors*"), out);
    }

    @Test
    void placeWithoutCoordinatesIsEnough() {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1)).stopPlace("Hagen, DE").tales("x").build());

        String html = exporter.exportTripAsHtml(trip);

        assertTrue(html.contains("<span class=\"fact-value\">→ Hagen, DE</span><span class=\"fact-note\">Destination</span></div>"), html);
        assertFalse(html.contains("class=\"fact-detail\""), html);
    }

    @Test
    void noPlacesNoOsmCredit() {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1)).startLat(1.0).startLon(2.0).tales("x").build());

        assertFalse(exporter.exportTrip(trip).contains("OpenStreetMap"));
    }

    @Test
    void htmlFactsShowPlacesWithTheirCoordinatesBelow() {
        setDefaultTripLanguage("de");
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1))
                .startLat(51.488).startLon(-0.013).startPlace("London, GB")
                .stopLat(50.1).stopLon(8.67).stopPlace("Frankfurt am Main, DE").tales("x").build());

        String html = exporter.exportTripAsHtml(trip);

        assertTrue(html.contains("<span class=\"fact-value\">London, GB</span><span class=\"fact-note\">→ Frankfurt am Main, DE</span>"
                + "<span class=\"fact-detail\">51°29′N 0°01′W → 50°06′N 8°40′E</span>"), html);
        assertTrue(html.contains("<p class=\"credits\">Place names © <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a> contributors</p>"), html);
    }

    @Test
    void exportTripAsHtmlRendersFactsStripInsteadOfByline() {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 3))
                .activity("cycling").distance(123.0).altitudeMeters(100.0).startLat(50.1).startLon(8.67)
                .title("A → B").tales("Tale.").build());

        String html = exporter.exportTripAsHtml(trip);

        assertTrue(html.contains("""
                <div class="facts">
                <div class="fact"><span class="fact-stamp"><small>Day</small>3</span><span class="fact-value">Thursday</span><span class="fact-note">July 3rd 2025</span></div>
                <div class="fact"><span class="fact-emoji">🚴</span><span class="fact-value">123.0 km</span><span class="fact-note">100 m climbed</span></div>
                <div class="fact"><span class="fact-emoji">🧭</span><span class="fact-value">50°06′N 8°40′E</span><span class="fact-note">Starting point</span></div>
                </div>"""), html);
        assertFalse(html.contains("· Day 3"), "the byline is for plain Markdown only");
        assertTrue(html.indexOf("class=\"facts\"") < html.indexOf("<p>Tale.</p>"), html);
    }

    @Test
    void weatherShowsInTheBylineAndFactsStrip() {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1))
                .weather(new Weather(15, 22, 1, "Soest, DE")).tales("Nice weather.").build());

        String out = exporter.exportTrip(trip);
        assertTrue(out.contains("🌤 15–22° partly cloudy"), out);

        String html = exporter.exportTripAsHtml(trip);
        assertTrue(html.contains("<span class=\"fact-emoji\">🌤</span>"
                + "<span class=\"fact-value\">15–22°</span><span class=\"fact-note\">partly cloudy</span>"), html);
    }

    @Test
    void weatherWithEqualMinMaxShowsOneTemperature() {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1))
                .weather(new Weather(20, 20, 0, null)).tales("Steady.").build());

        String out = exporter.exportTrip(trip);
        assertTrue(out.contains("🌞 20° clear"), out);
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
        setImpressionsFilePattern(tempDir.toString() + "/${ENTRY_YEAR}${ENTRY_MONTH}${ENTRY_DAY}*.jpg");

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
        setImpressionsFilePattern(tempDir.toString() + "/${ENTRY_YEAR}${ENTRY_MONTH}${ENTRY_DAY}*.jpg");
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
        setImpressionsFilePattern(tempDir.toString() + "/${ENTRY_YEAR}${ENTRY_MONTH}${ENTRY_DAY}*.jpg");
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

        assertTrue(out.contains("## Two"), out);
        assertTrue(out.contains("· Day 2 ·"), out);
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
    void exportDropsPrivateSections() {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1))
                .title("A → B").tales("Public text.\n\n## Internal\n\nKomoot said 87 km.").build());
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 2))
                .title("B → C").tales("## Private: costs\n\n45 EUR cash").build());

        String out = exporter.exportTrip(trip);

        assertTrue(out.contains("Public text."), out);
        assertFalse(out.contains("Internal"), out);
        assertFalse(out.contains("Komoot"), out);
        assertFalse(out.contains("45 EUR"), out);
        assertTrue(out.contains("## B → C"), "an all-private tale still exports its day heading");
    }

    @Test
    void exportShiftsTaleSubheadingsSoTheyDontCollideWithEntryHeading() {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1))
                .title("A → B").tales("Intro text.\n\n## Subheading\n\nMore detail.").build());

        String out = exporter.exportTrip(trip);

        assertTrue(out.contains("## A → B"), "entry heading should remain at H2");
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
        setImpressionsFilePattern(tempDir.toString() + "/${ENTRY_YEAR}${ENTRY_MONTH}${ENTRY_DAY}*.jpg");

        String markdown = exporter.exportTrip(trip);

        assertFalse(markdown.contains("IMPRESSIONS"));
        assertFalse(markdown.contains(".jpg"));
    }

    private void setDefaultTripLanguage(String code) {
        AppSettings settings = settingsStore.load();
        settings.setDefaultTripLanguage(code);
        settingsStore.save(settings);
    }

    @Test
    void germanTripLanguageTranslatesWordsDatesAndNumbers() {
        setDefaultTripLanguage("de");
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1))
                .distance(50.5).altitudeMeters(800.0).startLat(51.488).startLon(-0.013).title("A → B").tales("Los geht's.")
                .link("sleep", Link.parse("https://nice.hotel.com/"))
                .link("tour", Link.parse("https://tour.example.com/"))
                .build());

        String out = exporter.exportTrip(trip);

        assertTrue(out.contains("- Zeitraum: 1. Juli 2025 → 1. Juli 2025 (1 Tag, 1 Eintrag)"), out);
        assertTrue(out.contains("- Distanz: 50,5 km"), out);
        assertTrue(out.contains("- Höhenmeter: 800 m"), out);
        assertTrue(out.contains("## A → B\n\n*Dienstag, 1. Juli 2025 · Tag 1 · 📏 50,5 km, 800 m bergauf · 🧭 51°29′N 0°01′W*"), out);
        assertTrue(out.contains("* 🛏 Übernachten: [nice.hotel.com](https://nice.hotel.com/)"), out);
        // No German label for a custom verb: falls back to the capitalized verb
        assertTrue(out.contains("* 🔗 Tour: [tour.example.com](https://tour.example.com/)"), out);
    }

    @Test
    void germanTripLanguageTranslatesFactsStrip() {
        setDefaultTripLanguage("de");
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1))
                .activity("hiking").stopLat(50.1).stopLon(8.67).tales("hi").build());

        String html = exporter.exportTripAsHtml(trip);

        assertTrue(html.contains("<span class=\"fact-stamp\"><small>Tag</small>1</span><span class=\"fact-value\">Dienstag</span><span class=\"fact-note\">1. Juli 2025</span>"), html);
        assertTrue(html.contains("<span class=\"fact-emoji\">🚶</span><span class=\"fact-value\">Wandern</span></div>"), html);
        assertTrue(html.contains("<span class=\"fact-value\">→ 50°06′N 8°40′E</span><span class=\"fact-note\">Ziel</span>"), html);
        assertTrue(html.contains("<h2>Tag 1</h2>"), html);
    }

    @Test
    void germanTripLanguageSetsHtmlLang() {
        setDefaultTripLanguage("de");
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1)).tales("hi").build());

        assertTrue(exporter.exportTripAsHtml(trip).contains("<html lang=\"de\">"));
    }

    @Test
    void singleDayAndEntryUseSingular() {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1)).tales(".").build());

        assertTrue(exporter.exportTrip(trip).contains("(1 day, 1 entry)"));
    }

    @Test
    void textPlaceholderInTaleIsNotTranslated() {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "");
        store.saveTrip(trip);
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1)).tales("Literal {{t.day}} here").build());

        assertTrue(exporter.exportTrip(trip).contains("Literal {{t.day}} here"));
    }
}
