package net.timafe.triptale.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkTest {

    @Test
    void parsesBareUrl() {
        Link l = Link.parse(" https://mucfood.com/ ");
        assertEquals("https://mucfood.com/", l.url());
        assertNull(l.name());
        assertTrue(l.valid());
        assertEquals("https://mucfood.com/", l.format());
    }

    @Test
    void parsesNamedUrlAndRoundTrips() {
        Link l = Link.parse("[SuperTours](https://supertour-app.com)");
        assertEquals("SuperTours", l.name());
        assertEquals("https://supertour-app.com", l.url());
        assertEquals("[SuperTours](https://supertour-app.com)", l.format());
    }

    @Test
    void keepsParenthesesInUrl() {
        Link l = Link.parse("[Bonn](https://en.wikipedia.org/wiki/Bonn_(city))");
        assertEquals("Bonn", l.name());
        assertEquals("https://en.wikipedia.org/wiki/Bonn_(city)", l.url());
        assertTrue(l.valid());
    }

    @Test
    void stripsBracketsFromName() {
        assertEquals("[Bar Rooftop](https://bar.com)", Link.of("https://bar.com", "Bar [Rooftop]").format());
    }

    @Test
    void dropsBlankNameAndNameEqualToUrl() {
        assertNull(Link.of("https://bar.com", "  ").name());
        assertNull(Link.of("https://bar.com", "https://bar.com").name());
        assertEquals("https://bar.com", Link.parse("[https://bar.com](https://bar.com)").format());
    }

    @Test
    void keepsInvalidValueAsIs() {
        Link l = Link.parse("mucfood.com");
        assertFalse(l.valid());
        assertEquals("mucfood.com", l.format());
        assertEquals("mucfood.com", l.displayText());
        assertFalse(Link.of("ftp://x.com", null).valid());
    }

    @Test
    void displayTextIsNameElseHostWithoutWww() {
        assertEquals("Bistro", Link.of("https://www.bistro.com/x", "Bistro").displayText());
        assertEquals("bistro.com", Link.parse("https://www.bistro.com/x").displayText());
        assertEquals("nice.hotel.com", Link.parse("https://nice.hotel.com/").displayText());
    }

    @Test
    void entryOrdersLinksAlphabeticallyByVerb() {
        DiaryEntry e = DiaryEntry.builder(LocalDate.of(2026, 7, 24)).links(Map.of(
                "zoo", Link.parse("https://z.com"),
                "sleep", Link.parse("https://s.com"),
                "tour", Link.parse("https://t.com"),
                "track", Link.parse("https://tr.com"),
                "eat", Link.parse("https://e.com"))).build();
        assertEquals(List.of("eat", "sleep", "tour", "track", "zoo"), List.copyOf(e.links().keySet()));
    }

    @Test
    void labelsAndEmojiForKnownAndUnknownVerbs() {
        assertEquals("Sleep at", LinkKind.labelFor("sleep"));
        assertEquals("🛏", LinkKind.emojiFor("sleep"));
        assertEquals("Tour", LinkKind.labelFor("tour"));
        assertEquals(LinkKind.UNKNOWN_EMOJI, LinkKind.emojiFor("tour"));
    }
}
