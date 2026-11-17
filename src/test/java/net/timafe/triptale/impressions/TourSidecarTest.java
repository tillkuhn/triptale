package net.timafe.triptale.impressions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TourSidecarTest {

    @TempDir
    Path tempDir;

    private static String comment(String user, String text) {
        return "{\"text\":\"" + text + "\",\"_embedded\":{\"creator\":{\"username\":\"" + user + "\"}}}";
    }

    private Path write(String tourExtra, String comments) throws IOException {
        Path f = tempDir.resolve("t.json");
        Files.writeString(f, "{\"tour\":{" + tourExtra + "\"_embedded\":{\"creator\":{\"username\":\"me\"}}},"
                + "\"comments\":[" + comments + "]}");
        return f;
    }

    @Test
    void descriptionWinsAndIsStripped() throws IOException {
        Path f = write("\"description\":\"  Nice ride \",", comment("me", "ignored"));
        assertEquals("Nice ride", TourSidecar.tourText(f).orElseThrow());
    }

    @Test
    void blankOrAbsentDescriptionFallsBackToOwnCommentsJoined() throws IOException {
        Path f = write("\"description\":\"\",",
                comment("me", "first\\nline") + "," + comment("other", "hi") + "," + comment("me", "second"));
        assertEquals("first\nline\n\nsecond", TourSidecar.tourText(f).orElseThrow());
        Path g = write("", comment("me", "only"));
        assertEquals("only", TourSidecar.tourText(g).orElseThrow());
    }

    @Test
    void foreignCommentsOnlyOrBrokenFileYieldNothing() throws IOException {
        assertTrue(TourSidecar.tourText(write("", comment("other", "hi"))).isEmpty());
        Path bad = tempDir.resolve("bad.json");
        Files.writeString(bad, "not json");
        assertTrue(TourSidecar.tourText(bad).isEmpty());
        assertTrue(TourSidecar.tourText(tempDir.resolve("missing.json")).isEmpty());
    }

    @Test
    void webLink_namedFromUrl() throws IOException {
        Path f = tempDir.resolve("w.json");
        Files.writeString(f, "{\"web_url\":\"https://www.komoot.com/tour/3016247348\",\"tour\":{}}");
        var link = TourSidecar.webLink(f).orElseThrow();
        assertEquals("https://www.komoot.com/tour/3016247348", link.url());
        assertEquals("Komoot Tour 3016247348", link.name());
    }

    @Test
    void webLink_absentOrInvalidIsEmpty() throws IOException {
        Path f = tempDir.resolve("n.json");
        Files.writeString(f, "{\"web_url\":\"nope\"}");
        assertTrue(TourSidecar.webLink(f).isEmpty());
        Files.writeString(f, "{}");
        assertTrue(TourSidecar.webLink(f).isEmpty());
    }
}
