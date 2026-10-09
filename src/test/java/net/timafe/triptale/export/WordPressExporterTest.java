package net.timafe.triptale.export;

import net.timafe.triptale.attachments.AttachmentsDir;
import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.config.TripTaleProperties;
import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.impressions.ImpressionsService;
import net.timafe.triptale.storage.ImpressionsResolver;
import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.storage.PathPatternResolver;
import net.timafe.triptale.storage.SettingsStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WordPressExporterTest {

    private static final LocalDate DAY1 = LocalDate.of(2025, 7, 1);
    private static final LocalDate DAY2 = LocalDate.of(2025, 7, 2);
    private static final LocalDate DAY3 = LocalDate.of(2025, 7, 3);

    @TempDir
    Path tempDir;

    private MarkdownStore store;
    private SettingsStore settingsStore;
    private AttachmentsDir attachmentsDir;
    private WordPressExporter exporter;
    private Trip trip;

    @BeforeEach
    void setUp() {
        TripTaleProperties props = new TripTaleProperties();
        props.setSettingsDir(tempDir.resolve("settings").toString());
        settingsStore = new SettingsStore(props);
        AppSettings settings = new AppSettings();
        settings.setDataDir(tempDir.resolve("data").toString());
        settingsStore.save(settings);
        store = new MarkdownStore(settingsStore);
        attachmentsDir = new AttachmentsDir(store);
        ImpressionsService impressions = new ImpressionsService(
                new ImpressionsResolver(new PathPatternResolver()), attachmentsDir, settingsStore);
        exporter = new WordPressExporter(store, impressions, attachmentsDir, settingsStore);
        trip = new Trip(2025, "alps-2025", "Alps 2025", DAY1, null, "Summer ride");
        store.saveTrip(trip);
    }

    private void updateSettings(Consumer<AppSettings> change) {
        AppSettings settings = settingsStore.load();
        change.accept(settings);
        settingsStore.save(settings);
    }

    private void addImages(LocalDate day, String... names) throws IOException {
        Path dir = attachmentsDir.ensureDayDir(trip.ref(), day);
        for (String name : names) Files.createFile(dir.resolve(name));
    }

    @Test
    void exportsOnlyTheRangeAsDaysWithoutTripHeader() {
        store.saveEntry(trip.ref(), DiaryEntry.builder(DAY1).title("A → B").tales("one").build());
        store.saveEntry(trip.ref(), DiaryEntry.builder(DAY2).title("B → C").tales("two").build());
        store.saveEntry(trip.ref(), DiaryEntry.builder(DAY3).title("C → D").tales("three").build());

        String out = exporter.export(trip, DAY2, DAY3, false, false);

        assertTrue(out.startsWith("<!-- wp:heading -->\n<h2 class=\"wp-block-heading\">2025-07-02 Wednesday Day 2: B → C</h2>\n<!-- /wp:heading -->"), out);
        assertTrue(out.contains("<!-- wp:paragraph -->\n<p>three</p>\n<!-- /wp:paragraph -->"));
        assertFalse(out.contains("one"));
        assertFalse(out.contains("Alps 2025"));
        assertFalse(out.contains("Summer ride"));
    }

    @Test
    void statsParagraphHasDistanceClimbAndTrackLink() {
        store.saveEntry(trip.ref(), DiaryEntry.builder(DAY1).distance(42.0).altitudeMeters(650.4)
                .trackUrl("https://example.com/t?a=1&b=2").startLat(1.0).startLon(2.0).build());

        String out = exporter.export(trip, null, null, false, false);

        assertTrue(out.contains("<p>42.0 km · 650 m ↑ · <a href=\"https://example.com/t?a=1&amp;b=2\">Track</a></p>"), out);
        assertFalse(out.contains("Start"));
    }

    @Test
    void zeroOrUnsetStatsAreLeftOut() {
        store.saveEntry(trip.ref(), DiaryEntry.builder(DAY1).distance(0.0).altitudeMeters(0.0).tales("train day").build());

        String out = exporter.export(trip, null, null, false, false);

        assertFalse(out.contains(" km"));
        assertFalse(out.contains("m ↑"));
    }

    @Test
    void taleMarkdownMapsToCoreBlocks() {
        String tale = """
                First line
                same paragraph with **bold**.

                # Sub

                - a
                - b

                3. three
                4. four

                > quoted

                ```
                x < y
                ```

                ---

                - outer
                  - inner
                """;
        store.saveEntry(trip.ref(), DiaryEntry.builder(DAY1).tales(tale).build());

        String out = exporter.export(trip, null, null, false, false);

        assertTrue(out.contains("<p>First line same paragraph with <strong>bold</strong>.</p>"), out);
        assertTrue(out.contains("<!-- wp:heading {\"level\":3} -->\n<h3 class=\"wp-block-heading\">Sub</h3>\n<!-- /wp:heading -->"), out);
        assertTrue(out.contains("<!-- wp:list -->\n<ul class=\"wp-block-list\">\n<!-- wp:list-item -->\n<li>a</li>\n<!-- /wp:list-item -->"), out);
        assertTrue(out.contains("<!-- wp:list {\"ordered\":true,\"start\":3} -->\n<ol class=\"wp-block-list\" start=\"3\">"), out);
        assertTrue(out.contains("<!-- wp:quote -->\n<blockquote class=\"wp-block-quote\"><!-- wp:paragraph -->\n<p>quoted</p>\n<!-- /wp:paragraph --></blockquote>\n<!-- /wp:quote -->"), out);
        assertTrue(out.contains("<!-- wp:code -->\n<pre class=\"wp-block-code\"><code>x &lt; y</code></pre>\n<!-- /wp:code -->"), out);
        assertTrue(out.contains("<!-- wp:separator -->\n<hr class=\"wp-block-separator has-alpha-channel-opacity\"/>"), out);
        // a nested list stays editable as Custom HTML instead of producing invalid list blocks
        assertTrue(out.contains("<!-- wp:html -->\n<ul>\n<li>outer\n<ul>"), out);
    }

    @Test
    void galleryHotlinksBackpackImagesWithEncodedUrls() throws IOException {
        updateSettings(s -> {
            s.getAttachments().setPublicBaseUrl("https://cdn.example.net");
            s.setImpressionsGridColumns(3);
        });
        store.saveEntry(trip.ref(), DiaryEntry.builder(DAY1).tales("hi").build());
        addImages(DAY1, "20250701_101010_KiwieckBlick+_mini.jpg", "Ä b.png", "track.gpx");

        String out = exporter.export(trip, null, null, true, false);

        String dayUrl = "https://cdn.example.net/2025/alps-2025/2025-07-01-Tuesday/";
        assertTrue(out.contains("<!-- wp:gallery {\"columns\":3,\"linkTo\":\"none\"} -->\n"
                + "<figure class=\"wp-block-gallery has-nested-images columns-3 is-cropped\">"), out);
        assertTrue(out.contains("<img src=\"" + dayUrl + "20250701_101010_KiwieckBlick%2B_mini.jpg\" alt=\"Kiwieck Blick\"/>"), out);
        assertTrue(out.contains("<img src=\"" + dayUrl + "%C3%84%20b.png\" alt=\"Ä b\"/>"), out);
        assertFalse(out.contains("track.gpx"));
    }

    @Test
    void favesOnlyReducesTheGallery() throws IOException {
        updateSettings(s -> {
            s.getAttachments().setPublicBaseUrl("https://cdn.example.net/");
            s.setImpressionsFaveFilter("*+*");
        });
        store.saveEntry(trip.ref(), DiaryEntry.builder(DAY1).build());
        addImages(DAY1, "fave+.jpg", "other.jpg");

        String out = exporter.export(trip, null, null, true, true);

        assertTrue(out.contains("fave%2B.jpg"));
        assertFalse(out.contains("other.jpg"));
    }

    @Test
    void noGalleryWithoutPublicBaseUrlOrWithoutImages() throws IOException {
        store.saveEntry(trip.ref(), DiaryEntry.builder(DAY1).build());
        addImages(DAY1, "summit.jpg");

        assertFalse(exporter.imagesAvailable());
        assertFalse(exporter.export(trip, null, null, true, false).contains("wp:gallery"));

        updateSettings(s -> s.getAttachments().setPublicBaseUrl("https://cdn.example.net/"));
        assertTrue(exporter.imagesAvailable());
        assertFalse(exporter.export(trip, null, null, false, false).contains("wp:gallery"));
        assertTrue(exporter.export(trip, null, null, true, false).contains("wp:gallery"));
    }

    @Test
    void altTextFromFileName() {
        assertEquals("Ooops ICE 1011 Faellt Aus", WordPressExporter.altText(Path.of("20260529_142327_OoopsICE1011FaelltAus+_mini.jpg")));
        assertEquals("Stuttgarter Hofbraeu", WordPressExporter.altText(Path.of("20260529_185352_StuttgarterHofbraeu+_mini.jpg")));
        assertEquals("IMG 1234", WordPressExporter.altText(Path.of("IMG_1234.JPG")));
    }

    @Test
    void previewWrapsMarkupInShell() {
        String html = exporter.previewHtml("A & B", "<p>x</p>");

        assertTrue(html.contains("<title>A &amp; B</title>"));
        assertTrue(html.contains("<p>x</p>"));
    }
}
