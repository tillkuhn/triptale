package net.timafe.triptale.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeatherCodeTest {

    @Test
    void fromWmo_mapsKnownCodesToTheirBucket() {
        assertEquals(WeatherCode.CLEAR, WeatherCode.fromWmo(0));
        assertEquals(WeatherCode.PARTLY_CLOUDY, WeatherCode.fromWmo(1));
        assertEquals(WeatherCode.PARTLY_CLOUDY, WeatherCode.fromWmo(2));
        assertEquals(WeatherCode.CLOUDY, WeatherCode.fromWmo(3));
        assertEquals(WeatherCode.FOG, WeatherCode.fromWmo(45));
        assertEquals(WeatherCode.FOG, WeatherCode.fromWmo(48));
        assertEquals(WeatherCode.DRIZZLE, WeatherCode.fromWmo(51));
        assertEquals(WeatherCode.DRIZZLE, WeatherCode.fromWmo(57));
        assertEquals(WeatherCode.RAIN, WeatherCode.fromWmo(61));
        assertEquals(WeatherCode.RAIN, WeatherCode.fromWmo(82));
        assertEquals(WeatherCode.SNOW, WeatherCode.fromWmo(71));
        assertEquals(WeatherCode.SNOW, WeatherCode.fromWmo(86));
        assertEquals(WeatherCode.THUNDERSTORM, WeatherCode.fromWmo(95));
        assertEquals(WeatherCode.THUNDERSTORM, WeatherCode.fromWmo(99));
    }

    @Test
    void fromWmo_unrecognizedCodeFallsBackToCloudy() {
        assertEquals(WeatherCode.CLOUDY, WeatherCode.fromWmo(-1));
        assertEquals(WeatherCode.CLOUDY, WeatherCode.fromWmo(1000));
    }

    @Test
    void keyAndEmojiAreExposed() {
        assertEquals("partly_cloudy", WeatherCode.PARTLY_CLOUDY.key());
        assertEquals("🌤", WeatherCode.PARTLY_CLOUDY.emoji());
    }

    @Test
    void wmoCodeRoundTripsThroughFromWmoForEveryBucket() {
        for (WeatherCode bucket : WeatherCode.values()) {
            assertEquals(bucket, WeatherCode.fromWmo(bucket.wmoCode()),
                    bucket + "'s representative WMO code " + bucket.wmoCode() + " must map back to itself");
        }
    }
}
