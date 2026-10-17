package net.timafe.triptale.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WeatherTest {

    @Test
    void parse_minMaxCodeWithoutPlace() {
        Weather w = Weather.parse("15;22;1");
        assertEquals(new Weather(15, 22, 1, null), w);
        assertEquals("15;22;1", w.toRaw());
    }

    @Test
    void parse_keepsEmbeddedCommaInPlace() {
        Weather w = Weather.parse("15;22;1;Soest, DE");
        assertEquals(new Weather(15, 22, 1, "Soest, DE"), w);
        assertEquals("15;22;1;Soest, DE", w.toRaw());
    }

    @Test
    void parse_trimsWhitespaceAroundNumbers() {
        Weather w = Weather.parse(" 15 ; 22 ; 1 ");
        assertEquals(new Weather(15, 22, 1, null), w);
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
    void negativeTemperaturesRoundTrip() {
        Weather w = Weather.parse("-5;2;71");
        assertEquals(new Weather(-5, 2, 71, null), w);
        assertEquals("-5;2;71", w.toRaw());
    }
}
