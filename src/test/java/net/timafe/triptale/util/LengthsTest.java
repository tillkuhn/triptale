package net.timafe.triptale.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LengthsTest {

    @Test
    void distance_bareNumberIsKm() {
        assertEquals(101.64, Lengths.parseDistanceKm("101.64"));
    }

    @Test
    void distance_acceptsKmSuffixWithOrWithoutSpace() {
        assertEquals(101.64, Lengths.parseDistanceKm("101.64 km"));
        assertEquals(101.64, Lengths.parseDistanceKm("101.64km"));
        assertEquals(101.64, Lengths.parseDistanceKm("  101.64 KM  "));
    }

    @Test
    void distance_convertsMetresAndRoundsToTwoDecimals() {
        assertEquals(101.64, Lengths.parseDistanceKm("101640 m"));
        assertEquals(1.24, Lengths.parseDistanceKm("1235m"));
        assertEquals(0.85, Lengths.parseDistanceKm("850 m"));
    }

    @Test
    void distance_roundsPlainInputToTwoDecimals() {
        assertEquals(12.35, Lengths.parseDistanceKm("12.345"));
    }

    @Test
    void acceptsCommaAsDecimalSeparator() {
        assertEquals(12.5, Lengths.parseDistanceKm("12,5 km"));
        assertEquals(12.0, Lengths.parseDistanceKm("12,"));
        assertEquals(0.5, Lengths.parseDistanceKm(",5"));
    }

    @Test
    void altitude_bareNumberIsMetresRoundedToWhole() {
        assertEquals(493.0, Lengths.parseAltitudeM("492.83"));
        assertEquals(493.0, Lengths.parseAltitudeM("492.83 m"));
    }

    @Test
    void altitude_convertsKm() {
        assertEquals(1200.0, Lengths.parseAltitudeM("1.2 km"));
        assertEquals(1235.0, Lengths.parseAltitudeM("1,2345km"));
    }

    @Test
    void zeroIsAValue() {
        assertEquals(0.0, Lengths.parseDistanceKm("0"));
        assertEquals(0.0, Lengths.parseAltitudeM("0 m"));
    }

    @Test
    void blankIsNull() {
        assertNull(Lengths.parseDistanceKm(null));
        assertNull(Lengths.parseDistanceKm(""));
        assertNull(Lengths.parseAltitudeM("   "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "-5", "-5 km", "5 miles", "5 ft", "5 km 3", "km", "1e3", "NaN",
            "Infinity", "1.2.3", "5 k m", "."})
    void rejectsEverythingElse(String input) {
        assertThrows(IllegalArgumentException.class, () -> Lengths.parseDistanceKm(input));
        assertThrows(IllegalArgumentException.class, () -> Lengths.parseAltitudeM(input));
    }

    @Test
    void formatsWithUnitAndStripsTrailingZeros() {
        assertEquals("101.64 km", Lengths.formatDistance(101.64));
        assertEquals("101.6 km", Lengths.formatDistance(101.6));
        assertEquals("100 km", Lengths.formatDistance(100.0));
        assertEquals("0 km", Lengths.formatDistance(0.0));
        assertEquals("493 m", Lengths.formatAltitude(492.83));
        assertEquals("1200 m", Lengths.formatAltitude(1200.0));
    }

    @Test
    void formatsNullAsEmpty() {
        assertEquals("", Lengths.formatDistance(null));
        assertEquals("", Lengths.formatAltitude(null));
    }

    @Test
    void formattedTextParsesBackToSameValue() {
        assertEquals(101.64, Lengths.parseDistanceKm(Lengths.formatDistance(101.64)));
        assertEquals(493.0, Lengths.parseAltitudeM(Lengths.formatAltitude(492.83)));
    }
}
