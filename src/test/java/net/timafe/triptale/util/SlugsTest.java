package net.timafe.triptale.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SlugsTest {

    @Test
    void capitalizesEachWordAndJoinsWithoutSeparators() {
        assertEquals("TourDeFrance", Slugs.toSlug("Tour de France"));
    }

    @Test
    void keepsDigitsAndTreatsDashesAsWordSeparators() {
        assertEquals("BayernTour2025", Slugs.toSlug("Bayern-Tour 2025"));
    }

    @Test
    void stripsDiacritics() {
        assertEquals("ReiseNachKoln", Slugs.toSlug("Reise nach Köln"));
        assertEquals("CafeCreme", Slugs.toSlug("Café Crème"));
    }

    @Test
    void collapsesRepeatedWhitespaceAndDashes() {
        assertEquals("MultiSpaces", Slugs.toSlug("  multi   spaces  "));
        assertEquals("SideTrip", Slugs.toSlug("Side -- Trip"));
    }

    @Test
    void trimsLeadingAndTrailingDashes() {
        assertEquals("Edges", Slugs.toSlug("---edges---"));
    }

    @Test
    void dropsPunctuationThatIsNotAWordSeparator() {
        assertEquals("HelloWorld", Slugs.toSlug("Hello, world!"));
    }

    @Test
    void preservesExistingInternalCapitalization() {
        assertEquals("MyHikeInBavaria", Slugs.toSlug("My Hike in Bavaria :-)"));
        assertEquals("McDonaldTrip", Slugs.toSlug("McDonald Trip"));
    }

    @Test
    void rejectsNullInput() {
        assertThrows(IllegalArgumentException.class, () -> Slugs.toSlug(null));
    }

    @Test
    void rejectsBlankInput() {
        assertThrows(IllegalArgumentException.class, () -> Slugs.toSlug("   "));
    }

    @Test
    void rejectsInputThatNormalizesToEmpty() {
        assertThrows(IllegalArgumentException.class, () -> Slugs.toSlug("!!!"));
    }
}
