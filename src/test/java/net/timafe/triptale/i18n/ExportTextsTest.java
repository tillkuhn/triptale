package net.timafe.triptale.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportTextsTest {

    private static final Locale ORIGINAL_DEFAULT = Locale.getDefault();
    private static final LocalDate JULY_23 = LocalDate.of(2026, 7, 23);

    private final ExportTexts en = ExportTexts.of(TripLanguage.EN);
    private final ExportTexts de = ExportTexts.of(TripLanguage.DE);

    @AfterEach
    void restoreDefaultLocale() {
        Locale.setDefault(ORIGINAL_DEFAULT);
    }

    @Test
    void englishStaysEnglishOnAGermanJvm() {
        Locale.setDefault(Locale.GERMANY);

        ExportTexts texts = ExportTexts.of(TripLanguage.EN);

        assertEquals("Distance", texts.word("stats.distance"));
        assertEquals("Thursday", texts.weekday(JULY_23));
        assertEquals("12.5", texts.distance(12.5));
    }

    @Test
    void germanWordsUmlautsIntact() {
        assertEquals("Distanz", de.word("stats.distance"));
        assertEquals("Höhenmeter", de.word("stats.altitude"));
        assertEquals("Tag", de.word("day"));
    }

    @Test
    void activityLabels() {
        assertEquals("🚴 Radeln", de.activity("cycling"));
        assertEquals("🚶 Wandern", de.activity("hiking"));
        assertEquals("🚆 Railtripping", de.activity("railtripping"));
        assertEquals("🚴 Cycling", en.activity("cycling"));
        assertEquals("❓ Kayaking", de.activity("kayaking"));
        assertEquals("", en.activity(null));
        assertEquals("", en.activity(" "));
    }

    @Test
    void plurals() {
        assertEquals("day", en.plural("days", 1));
        assertEquals("days", en.plural("days", 2));
        assertEquals("days", en.plural("days", 0));
        assertEquals("Tag", de.plural("days", 1));
        assertEquals("Einträge", de.plural("entries", 3));
    }

    @Test
    void linkLabels() {
        assertEquals("Sleep at", en.linkLabel("sleep"));
        assertEquals("Übernachten", de.linkLabel("sleep"));
        assertEquals("Hike", de.linkLabel("hike"));
    }

    @Test
    void weatherLabels() {
        assertEquals("partly cloudy", en.weatherLabel(1));
        assertEquals("teils bewölkt", de.weatherLabel(1));
        assertEquals("rain", en.weatherLabel(63));
        assertEquals("Regen", de.weatherLabel(63));
        // Unrecognized code falls back to the cloudy bucket
        assertEquals("cloudy", en.weatherLabel(-1));
    }

    @Test
    void elevation() {
        assertEquals("124 m a.s.l.", en.elevation(124));
        assertEquals("124 m üNN", de.elevation(124));
        assertEquals("-430 m a.s.l.", en.elevation(-430));
    }

    @Test
    void longDates() {
        assertEquals("July 23rd 2026", en.longDate(JULY_23));
        assertEquals("23. Juli 2026", de.longDate(JULY_23));
        assertEquals("Donnerstag", de.weekday(JULY_23));
    }

    @ParameterizedTest
    @CsvSource({"1,st", "2,nd", "3,rd", "4,th", "11,th", "12,th", "13,th", "21,st", "22,nd", "23,rd", "31,st"})
    void ordinalSuffix(int day, String suffix) {
        assertEquals(suffix, ExportTexts.ordinalSuffix(day));
    }

    @Test
    void numbersFollowTripLocaleWithoutGrouping() {
        assertEquals("12,5", de.distance(12.5));
        assertEquals("1234", de.altitude(1234.4));
        assertEquals("1234", en.altitude(1234.4));
    }

    @Test
    void germanKeysExistInEnglishBase() throws IOException {
        Properties base = load("/i18n/export.properties");
        Properties german = load("/i18n/export_de.properties");
        for (String key : german.stringPropertyNames()) {
            // link.* and activity.* labels have their English default in LinkKind / Activity
            if (key.startsWith("link.") || key.startsWith("activity.")) continue;
            assertTrue(base.containsKey(key), "export_de.properties has a key missing from the base: " + key);
        }
    }

    private static Properties load(String resource) throws IOException {
        try (InputStream in = ExportTextsTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            Properties p = new Properties();
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return p;
        }
    }
}
