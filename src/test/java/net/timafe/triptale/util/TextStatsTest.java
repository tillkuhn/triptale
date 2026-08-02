package net.timafe.triptale.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextStatsTest {

    @Test
    void countsWhitespaceSeparatedWords() {
        assertEquals(3, TextStats.wordCount("hello brave world"));
    }

    @Test
    void collapsesRepeatedWhitespaceAndNewlines() {
        assertEquals(2, TextStats.wordCount("hello\n\n   world"));
    }

    @Test
    void trimsLeadingAndTrailingWhitespace() {
        assertEquals(1, TextStats.wordCount("   solo   "));
    }

    @Test
    void returnsZeroForNull() {
        assertEquals(0, TextStats.wordCount(null));
    }

    @Test
    void returnsZeroForBlank() {
        assertEquals(0, TextStats.wordCount("   "));
        assertEquals(0, TextStats.wordCount(""));
    }
}
