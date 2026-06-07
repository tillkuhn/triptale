package net.timafe.triptale.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SlugsTest {

    @Test
    void lowercasesAndReplacesWhitespaceWithDashes() {
        assertEquals("tour-de-france", Slugs.toSlug("Tour de France"));
    }

    @Test
    void keepsDigitsAndExistingDashes() {
        assertEquals("bayern-tour-2025", Slugs.toSlug("Bayern-Tour 2025"));
    }

    @Test
    void stripsDiacritics() {
        assertEquals("reise-nach-koln", Slugs.toSlug("Reise nach Köln"));
        assertEquals("cafe-creme", Slugs.toSlug("Café Crème"));
    }

    @Test
    void collapsesRepeatedWhitespaceAndDashes() {
        assertEquals("multi-spaces", Slugs.toSlug("  multi   spaces  "));
        assertEquals("side-trip", Slugs.toSlug("Side -- Trip"));
    }

    @Test
    void trimsLeadingAndTrailingDashes() {
        assertEquals("edges", Slugs.toSlug("---edges---"));
    }

    @Test
    void dropsPunctuationOtherThanDashAndUnderscore() {
        assertEquals("hello-world", Slugs.toSlug("Hello, world!"));
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
