package net.timafe.triptale.export;

import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.config.TripTaleProperties;
import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.domain.Trip;
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

class SocialMediaExporterTest {

    @TempDir
    Path tempDir;

    private MarkdownStore store;
    private SettingsStore settingsStore;
    private SocialMediaExporter exporter;

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
        exporter = new SocialMediaExporter(store, settingsStore);
    }

    private Trip aTrip() {
        Trip trip = new Trip(2025, "alps-2025", "Alps 2025", LocalDate.of(2025, 7, 1), null, "Summer ride");
        store.saveTrip(trip);
        return trip;
    }

    @Test
    void noHeadingsFactsLinksOrMarkdownSyntax() {
        Trip trip = aTrip();
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1))
                .distance(50.0).altitudeMeters(800.0).title("A → B")
                .tales("## Morning\n\nWe left **early** and rode through the *valley*, passing a "
                        + "[waterfall](https://example.com).\n\n> a quote to flatten\n\n```\ncode stays out\n```")
                .build());

        String out = exporter.export(trip, null, null);

        assertFalse(out.contains("#"), "no headings");
        assertFalse(out.contains("*"), "no emphasis markers");
        assertFalse(out.contains("["), "no link brackets");
        assertFalse(out.contains("https://example.com"), "no URLs");
        assertFalse(out.contains("```"), "no fences");
        assertFalse(out.contains("code stays out"), "code block content dropped");
        assertFalse(out.contains("50.0 km"), "no distance fact");
        assertFalse(out.contains("800"), "no altitude fact");
        assertFalse(out.contains("A → B"), "no title heading");
        assertTrue(out.contains("We left early and rode through the valley, passing a waterfall."), out);
        assertTrue(out.contains("a quote to flatten"), "blockquote text kept");
    }

    @Test
    void paragraphsWithinADayKeepBlankLinesBetweenThemSelvesAndDaysGetAWiderGap() {
        Trip trip = aTrip();
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1))
                .title("Day one").tales("First paragraph.\n\nSecond paragraph.").build());
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 2))
                .title("Day two").tales("Only paragraph.").build());

        String out = exporter.export(trip, null, null);

        assertEquals("First paragraph.\n\nSecond paragraph.\n\n\nOnly paragraph.\n\n\nTo be continued ...\n", out);
    }

    @Test
    void privateSectionsAreStripped() {
        Trip trip = aTrip();
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1))
                .title("Day one").tales("Public text.\n\n## Private\n\nSecret stuff.").build());

        String out = exporter.export(trip, null, null);

        assertTrue(out.contains("Public text."));
        assertFalse(out.contains("Secret stuff."));
    }

    @Test
    void daysWithNoTaleAreSkippedEntirely() {
        Trip trip = aTrip();
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1))
                .title("Empty day").build());
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 2))
                .title("Day two").tales("Some text.").build());

        String out = exporter.export(trip, null, null);

        assertEquals("Some text.\n\n\nTo be continued ...\n", out);
    }

    @Test
    void emptyRangeYieldsEmptyString() {
        Trip trip = aTrip();

        assertEquals("", exporter.export(trip, null, null));
    }

    @Test
    void appendsToBeContinuedTeaserInTripLanguage() {
        Trip trip = aTrip();
        store.saveEntry(trip.ref(), DiaryEntry.builder(LocalDate.of(2025, 7, 1))
                .title("Day one").tales("Some text.").build());

        assertTrue(exporter.export(trip, null, null).endsWith("Some text.\n\n\nTo be continued ...\n"));

        AppSettings settings = settingsStore.load();
        settings.setDefaultTripLanguage("de");
        settingsStore.save(settings);

        assertTrue(exporter.export(trip, null, null).endsWith("Some text.\n\n\nFortsetzung folgt ...\n"));
    }

    @Test
    void previewHtmlWrapsParagraphsAndEscapesHtml() {
        String html = exporter.previewHtml("A & B", "First <tag>.\n\nSecond.");

        assertTrue(html.contains("<title>A &amp; B</title>"));
        assertTrue(html.contains("<p>First &lt;tag&gt;.</p>"));
        assertTrue(html.contains("<p>Second.</p>"));
    }
}
