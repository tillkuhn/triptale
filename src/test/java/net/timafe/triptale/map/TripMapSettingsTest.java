package net.timafe.triptale.map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TripMapSettingsTest {

    private static final TripMapSettings SETTINGS = new TripMapSettings(LocalDate.of(2026, 9, 20),
            LocalDate.of(2026, 9, 23), TileSource.CYCLOSM, OsmMapRenderer.ZoomLevel.WIDE, MapFormat.PORTRAIT,
            true, 4, true, 60, 212);

    @Test
    void roundTripsThroughText() {
        assertEquals(SETTINGS, TripMapSettings.fromText(SETTINGS.toText()).orElseThrow());
    }

    @Test
    void roundTripsThroughAPngFile(@TempDir Path dir) throws Exception {
        Path png = dir.resolve("tripmap.png");
        PngText.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), png, SETTINGS.toText());
        assertEquals(SETTINGS, TripMapSettings.fromText(PngText.read(png)).orElseThrow());
    }

    @Test
    void noRange_isEmpty(@TempDir Path dir) throws Exception {
        Path png = dir.resolve("plain.png");
        PngText.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), png, Map.of());
        assertTrue(PngText.read(png).isEmpty());
        assertTrue(TripMapSettings.fromText(Map.of()).isEmpty());
    }

    @Test
    void reversedRange_isEmpty() {
        Map<String, String> text = new HashMap<>(SETTINGS.toText());
        text.put("triptale:to", "2026-09-01");
        assertTrue(TripMapSettings.fromText(text).isEmpty());
    }

    @Test
    void unknownStyleZoomOrFormat_fallBackToDefaults() {
        Map<String, String> text = new HashMap<>(SETTINGS.toText());
        text.put("triptale:style", "gone");
        text.put("triptale:zoom", "LUDICROUS");
        text.put("triptale:format", "CINEMASCOPE");
        TripMapSettings parsed = TripMapSettings.fromText(text).orElseThrow();
        assertEquals(TileSource.OPEN_TOPO_MAP, parsed.source());
        assertEquals(OsmMapRenderer.DEFAULT_ZOOM, parsed.zoom());
        assertEquals(MapFormat.DEFAULT, parsed.format());
        assertEquals(SETTINGS.from(), parsed.from());
    }
}
