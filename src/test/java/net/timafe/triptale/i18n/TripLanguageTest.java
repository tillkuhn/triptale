package net.timafe.triptale.i18n;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TripLanguageTest {

    @Test
    void fromCode() {
        assertEquals(TripLanguage.DE, TripLanguage.fromCode("de"));
        assertEquals(TripLanguage.DE, TripLanguage.fromCode(" DE "));
        assertEquals(TripLanguage.EN, TripLanguage.fromCode("en"));
    }

    @Test
    void blankOrUnknownFallsBackToEnglish() {
        assertEquals(TripLanguage.EN, TripLanguage.fromCode(null));
        assertEquals(TripLanguage.EN, TripLanguage.fromCode(""));
        assertEquals(TripLanguage.EN, TripLanguage.fromCode("fr"));
    }
}
