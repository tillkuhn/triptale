package net.timafe.triptale.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WeatherTest {

    @Test
    void parse_minMaxCodeWithoutElevationOrPlace() {
        Weather w = Weather.parse("15;22;1");
        assertEquals(new Weather(15, 22, 1, null, null), w);
        assertEquals("15;22;1", w.toRaw());
    }

    @Test
    void parse_withElevationNoPlace() {
        Weather w = Weather.parse("15;22;1;320");
        assertEquals(new Weather(15, 22, 1, 320, null), w);
        assertEquals("15;22;1;320", w.toRaw());
    }

    @Test
    void parse_withPlaceNoElevationUsesEmptyFourthField() {
        Weather w = Weather.parse("15;22;1;;Soest, DE");
        assertEquals(new Weather(15, 22, 1, null, "Soest, DE"), w);
        assertEquals("15;22;1;;Soest, DE", w.toRaw());
    }

    @Test
    void parse_keepsEmbeddedCommaInPlace() {
        Weather w = Weather.parse("15;22;1;320;Soest, DE");
        assertEquals(new Weather(15, 22, 1, 320, "Soest, DE"), w);
        assertEquals("15;22;1;320;Soest, DE", w.toRaw());
    }

    @Test
    void parse_trimsWhitespaceAroundNumbers() {
        Weather w = Weather.parse(" 15 ; 22 ; 1 ; 320 ");
        assertEquals(new Weather(15, 22, 1, 320, null), w);
    }

    @Test
    void parse_blankOrNullIsAbsent() {
        assertNull(Weather.parse(null));
        assertNull(Weather.parse(""));
        assertNull(Weather.parse("   "));
    }

    @Test
    void parse_malformedIsTreatedAsAbsentNotThrown() {
        assertNull(Weather.parse("not;a;weather;value"));
        assertNull(Weather.parse("15;22"));
        assertNull(Weather.parse("garbage"));
    }

    @Test
    void parse_legacyPlaceOnlyValueIsUnparseableUntilMigrated() {
        // Pre-todo-72 data ("min;max;code;place") is positionally identical to the new
        // "elevation, no place" shape — an unmigrated value fails to parse as a number and is
        // tolerated as absent, same as any other unparseable value (see scripts/migrate_weather_elevation.py).
        assertNull(Weather.parse("15;22;1;Soest, DE"));
    }

    @Test
    void negativeTemperaturesRoundTrip() {
        Weather w = Weather.parse("-5;2;71");
        assertEquals(new Weather(-5, 2, 71, null, null), w);
        assertEquals("-5;2;71", w.toRaw());
    }

    @Test
    void negativeElevationRoundTrips() {
        // Below sea level (e.g. Dead Sea shore) is a valid elevation reading, not an error.
        Weather w = Weather.parse("30;38;0;-430;Ein Bokek, IL");
        assertEquals(new Weather(30, 38, 0, -430, "Ein Bokek, IL"), w);
        assertEquals("30;38;0;-430;Ein Bokek, IL", w.toRaw());
    }
}
