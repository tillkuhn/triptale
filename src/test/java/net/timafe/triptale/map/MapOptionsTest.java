package net.timafe.triptale.map;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapOptionsTest {

    @Test
    void roundTripsThroughAMap() {
        MapOptions options = new MapOptions(TileSource.CYCLOSM, OsmMapRenderer.ZoomLevel.SNUG, MapFormat.PORTRAIT, false, true);
        assertEquals(options, MapOptions.fromMap(options.toMap()));
    }

    @Test
    void emptyMap_isTheDefault() {
        assertEquals(MapOptions.DEFAULT, MapOptions.fromMap(Map.of()));
    }

    @Test
    void legacyStyleOnly_keepsTheStyleAndDefaultsTheRest() {
        MapOptions options = MapOptions.fromMap(Map.of("style", TileSource.CYCLOSM.id()));
        assertEquals(new MapOptions(TileSource.CYCLOSM, MapOptions.DEFAULT.zoom(), MapOptions.DEFAULT.format(),
                MapOptions.DEFAULT.photos(), MapOptions.DEFAULT.route()), options);
    }

    @Test
    void unknownValues_fallBackOneByOne() {
        MapOptions options = MapOptions.fromMap(Map.of("style", "gone", "zoom", "LUDICROUS", "format", "IMAX",
                "photos", "maybe", "route", "true"));
        assertEquals(new MapOptions(MapOptions.DEFAULT.source(), MapOptions.DEFAULT.zoom(), MapOptions.DEFAULT.format(),
                MapOptions.DEFAULT.photos(), true), options);
    }
}
