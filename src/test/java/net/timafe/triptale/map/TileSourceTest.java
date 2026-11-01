package net.timafe.triptale.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TileSourceTest {

    @Test
    void tileUrl_formatsZoomXYAndRoundRobinsSubdomains() {
        TileSource source = TileSource.OPEN_TOPO_MAP;
        assertEquals("https://a.tile.opentopomap.org/5/10/12.png", source.tileUrl(5, 10, 12, 0));
        assertEquals("https://b.tile.opentopomap.org/5/10/12.png", source.tileUrl(5, 10, 12, 1));
        assertEquals("https://c.tile.opentopomap.org/5/10/12.png", source.tileUrl(5, 10, 12, 2));
        assertEquals("https://a.tile.opentopomap.org/5/10/12.png", source.tileUrl(5, 10, 12, 3), "wraps around");
    }

    @Test
    void connectivityUrl_usesTheFirstSubdomain() {
        assertEquals("https://a.tile.opentopomap.org", TileSource.OPEN_TOPO_MAP.connectivityUrl());
    }

    @Test
    void id_isLowercase() {
        assertEquals("open_topo_map", TileSource.OPEN_TOPO_MAP.id());
    }

    @Test
    void toString_isTheLabel() {
        assertEquals("OpenTopoMap", TileSource.OPEN_TOPO_MAP.toString());
    }

    @Test
    void attribution_creditsOpenStreetMapAndTheStyle() {
        assertTrue(TileSource.OPEN_TOPO_MAP.attribution().contains("OpenStreetMap"));
        assertTrue(TileSource.OPEN_TOPO_MAP.attribution().contains("OpenTopoMap"));
    }

    @Test
    void osmStandard_hasNoSubdomainPrefix() {
        assertEquals("https://tile.openstreetmap.org", TileSource.OSM_STANDARD.connectivityUrl());
        assertEquals("https://tile.openstreetmap.org/5/10/12.png", TileSource.OSM_STANDARD.tileUrl(5, 10, 12, 0));
        assertEquals("https://tile.openstreetmap.org/5/10/12.png", TileSource.OSM_STANDARD.tileUrl(5, 10, 12, 7),
                "single host regardless of request index");
    }

    @Test
    void cyclosm_roundRobinsItsOwnSubdomainsAndPath() {
        assertEquals("https://a.tile-cyclosm.openstreetmap.fr/cyclosm/5/10/12.png",
                TileSource.CYCLOSM.tileUrl(5, 10, 12, 0));
        assertEquals("https://b.tile-cyclosm.openstreetmap.fr/cyclosm/5/10/12.png",
                TileSource.CYCLOSM.tileUrl(5, 10, 12, 1));
    }

    @Test
    void requiresApiKey_isFalseForTheNoKeySources() {
        assertFalse(TileSource.OPEN_TOPO_MAP.requiresApiKey());
        assertFalse(TileSource.OSM_STANDARD.requiresApiKey());
        assertFalse(TileSource.CYCLOSM.requiresApiKey());
    }

    @Test
    void requiresApiKey_isTrueForThunderforestSources() {
        assertTrue(TileSource.THUNDERFOREST_OUTDOORS.requiresApiKey());
        assertTrue(TileSource.THUNDERFOREST_OPENCYCLEMAP.requiresApiKey());
    }

    @Test
    void tileUrl_noKeySourceIgnoresAnApiKeyArgument() {
        assertEquals("https://a.tile.opentopomap.org/5/10/12.png",
                TileSource.OPEN_TOPO_MAP.tileUrl(5, 10, 12, 0, "some-key"));
    }

    @Test
    void tileUrl_thunderforestAppendsTheApiKeyQueryParam() {
        assertEquals("https://a.tile.thunderforest.com/outdoors/5/10/12.png?apikey=abc123",
                TileSource.THUNDERFOREST_OUTDOORS.tileUrl(5, 10, 12, 0, "abc123"));
        assertEquals("https://b.tile.thunderforest.com/cycle/5/10/12.png?apikey=abc123",
                TileSource.THUNDERFOREST_OPENCYCLEMAP.tileUrl(5, 10, 12, 1, "abc123"));
    }

    @Test
    void tileUrl_thunderforestWithoutApiKeyArgumentAppendsAnEmptyValue() {
        assertEquals("https://a.tile.thunderforest.com/outdoors/5/10/12.png?apikey=",
                TileSource.THUNDERFOREST_OUTDOORS.tileUrl(5, 10, 12, 0));
    }

    @Test
    void thunderforest_attributionCreditsThunderforestAndOpenStreetMap() {
        assertTrue(TileSource.THUNDERFOREST_OUTDOORS.attribution().contains("Thunderforest"));
        assertTrue(TileSource.THUNDERFOREST_OUTDOORS.attribution().contains("OpenStreetMap"));
    }
}
