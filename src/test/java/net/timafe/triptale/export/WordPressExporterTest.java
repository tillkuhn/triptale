package net.timafe.triptale.export;

import net.timafe.triptale.attachments.AttachmentsDir;
import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.config.TripTaleProperties;
import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.domain.Link;
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

        assertTrue(out.startsWith("<!-- wp:heading -->\n<h2 class=\"wp-block-heading\">B → C</h2>\n<!-- /wp:heading -->"), out);
        assertTrue(out.contains("<!-- wp:paragraph -->\n<p>three</p>\n<!-- /wp:paragraph -->"));
        assertFalse(out.contains("one"));
        assertFalse(out.contains("Alps 2025"));
        assertFalse(out.contains("Summer ride"));
    }

    @Test
    void privateSectionsAreDropped() {
        store.saveEntry(trip.ref(), DiaryEntry.builder(DAY1).title("A → B")
                .tales("Public text.\n\n## Internal\n\nKomoot said 87 km.").build());
        store.saveEntry(trip.ref(), DiaryEntry.builder(DAY2).title("B → C")
                .tales("## Private\n\nall secret").build());

        String out = exporter.export(trip, null, null, false, false);

        assertTrue(out.contains("<p>Public text.</p>"), out);
        assertFalse(out.contains("Internal"), out);
        assertFalse(out.contains("Komoot"), out);
        assertFalse(out.contains("secret"), out);
        assertTrue(out.contains(">B → C</h2>"), out);
    }

    @Test
    void factsBecomeColumnsUnderTheHeading() {
        store.saveEntry(trip.ref(), DiaryEntry.builder(DAY1).activity("cycling").distance(42.0).altitudeMeters(650.4)
                .startLat(1.0).startLon(2.0).tales("tale").build());

        String out = exporter.export(trip, null, null, false, false);

        assertTrue(out.contains("""
                <!-- wp:column -->
                <div class="wp-block-column"><!-- wp:paragraph {"align":"center","fontSize":"x-large"} -->
                <p class="has-text-align-center has-x-large-font-size">🚴</p>
                <!-- /wp:paragraph -->

                <!-- wp:paragraph {"align":"center"} -->
                <p class="has-text-align-center"><strong>42.0 km</strong><br><small>650 m climbed</small></p>
                <!-- /wp:paragraph --></div>
                <!-- /wp:column -->"""), out);
        assertTrue(out.contains(">📅</p>"), out);
        assertTrue(out.contains("<strong>Tuesday · Day 1</strong><br><small>July 1st 2025</small>"), out);
        assertTrue(out.contains("<strong>1°00′N 2°00′E</strong><br><small>Starting point</small>"), out);
        assertTrue(out.contains("<!-- wp:columns -->\n<div class=\"wp-block-columns\"><!-- wp:column -->"), out);
        assertTrue(out.indexOf("</h2>") < out.indexOf("wp:columns") && out.indexOf("wp:columns") < out.indexOf("<p>tale</p>"), out);
    }

    @Test
    void linksBecomeListAfterTale() {
        store.saveEntry(trip.ref(), DiaryEntry.builder(DAY1).tales("tale")
                .link("sleep", Link.of("https://hotel.example.com/x", "Hotel <B>"))
                .link("track", Link.parse("https://www.example.com/t?a=1&b=2"))
                .link("tour", Link.parse("not a url"))
                .build());

        String out = exporter.export(trip, null, null, false, false);

        assertTrue(out.indexOf("<p>tale</p>") < out.indexOf("<!-- wp:list -->"), out);
        assertTrue(out.contains("<li>🗺 Track tour: <a href=\"https://www.example.com/t?a=1&amp;b=2\">example.com</a></li>"), out);
        assertTrue(out.contains("<li>🛏 Sleep at: <a href=\"https://hotel.example.com/x\">Hotel &lt;B&gt;</a></li>"), out);
        assertTrue(out.contains("<li>🔗 Tour: not a url</li>"), out);
        assertTrue(out.indexOf("Sleep at:") < out.indexOf("Track tour:"), out);
    }

    @Test
    void zeroOrUnsetStatsAreLeftOut() {
        store.saveEntry(trip.ref(), DiaryEntry.builder(DAY1).distance(0.0).altitudeMeters(0.0).tales("train day").build());

        String out = exporter.export(trip, null, null, false, false);

        assertFalse(out.contains(" km"));
        assertFalse(out.contains("climbed"));
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
        assertTrue(html.contains("<html lang=\"en\">"));
    }

    @Test
    void germanTripLanguageTranslatesHeadingStatsAndLinks() {
        updateSettings(s -> s.setDefaultTripLanguage("de"));
        store.saveEntry(trip.ref(), DiaryEntry.builder(DAY2).title("B → C").distance(42.5).altitudeMeters(650.0)
                .link("eat", Link.parse("https://food.example.com/")).build());

        String out = exporter.export(trip, null, null, false, false);

        assertTrue(out.contains(">B → C</h2>"), out);
        assertTrue(out.contains("<strong>Mittwoch · Tag 2</strong><br><small>2. Juli 2025</small>"), out);
        assertTrue(out.contains("<strong>42,5 km</strong><br><small>650 m bergauf</small>"), out);
        assertTrue(out.contains("<li>🍲 Essen: <a href=\"https://food.example.com/\">food.example.com</a></li>"), out);
        assertTrue(exporter.previewHtml("t", "").contains("<html lang=\"de\">"));
    }
}
