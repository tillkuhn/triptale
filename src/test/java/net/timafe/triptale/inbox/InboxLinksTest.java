package net.timafe.triptale.inbox;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InboxLinksTest {

    @Test
    void firstUrlFindsAUrlAmongOtherText() {
        assertEquals(Optional.of("https://my.rest.com"),
                InboxLinks.firstUrl("Nice food https://my.rest.com was great"));
    }

    @Test
    void firstUrlPicksTheEarliestWhenThereAreSeveral() {
        assertEquals(Optional.of("https://a.example"),
                InboxLinks.firstUrl("https://a.example and also https://b.example"));
    }

    @Test
    void firstUrlIsEmptyWithoutAUrl() {
        assertTrue(InboxLinks.firstUrl("Just a note, no link").isEmpty());
        assertTrue(InboxLinks.firstUrl("").isEmpty());
        assertTrue(InboxLinks.firstUrl(null).isEmpty());
    }

    @Test
    void nameWithoutUrlCollapsesMultilineTextAndDropsTheUrl() {
        assertEquals("Beldibi Turkish Bath",
                InboxLinks.nameWithoutUrl("Beldibi Turkish Bath\nhttps://maps.app.goo.gl/abc", "https://maps.app.goo.gl/abc"));
    }

    @Test
    void nameWithoutUrlKeepsFullTextWhenNoUrlGiven() {
        assertEquals("Nice food", InboxLinks.nameWithoutUrl("Nice food", null));
    }

    @Test
    void guessVerbMatchesWholeWordCaseInsensitive() {
        assertEquals(Optional.of("eat"),
                InboxLinks.guessVerb("Nice EAT spot", List.of("eat", "dive")));
    }

    @Test
    void guessVerbIgnoresPartialWordMatches() {
        assertTrue(InboxLinks.guessVerb("We went eating out", List.of("eat")).isEmpty());
    }

    @Test
    void guessVerbPicksTheEarliestKeywordInTheText() {
        assertEquals(Optional.of("dive"),
                InboxLinks.guessVerb("Dive and eat at the harbor", List.of("eat", "dive")));
    }

    @Test
    void guessVerbOnlyConsidersGivenKeys() {
        assertFalse(InboxLinks.guessVerb("Great sleep spot", List.of("eat", "dive")).isPresent());
    }

    @Test
    void guessVerbIsEmptyWithoutAnyMatch() {
        assertTrue(InboxLinks.guessVerb("Just a nice walk", List.of("eat", "dive")).isEmpty());
    }
}
