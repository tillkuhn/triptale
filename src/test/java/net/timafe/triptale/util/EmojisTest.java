package net.timafe.triptale.util;

import net.timafe.triptale.util.Emojis.Entry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmojisTest {

    @Test
    void parseSplitsGlyphAndKeywords() {
        List<Entry> parsed = Emojis.parse(List.of(
                "# header", "", " 🚶 | Walk, Walking, Hike ", "no separator", "☕|Coffee"));
        assertEquals(List.of(
                new Entry("🚶", List.of("walk", "walking", "hike")),
                new Entry("☕", List.of("coffee"))), parsed);
    }

    @Test
    void searchMatchesKeywordSubstringCaseInsensitively() {
        Emojis emojis = new Emojis(List.of(
                new Entry("🚶", List.of("walk", "walking", "hike", "hiking")),
                new Entry("☕", List.of("coffee"))));
        assertEquals(List.of(new Entry("🚶", List.of("walk", "walking", "hike", "hiking"))),
                emojis.search("HIKE"));
        assertTrue(emojis.search("zzz").isEmpty());
    }

    @Test
    void blankSearchReturnsEverything() {
        Emojis emojis = new Emojis(List.of(
                new Entry("🚶", List.of("hike")),
                new Entry("☕", List.of("coffee"))));
        assertEquals(emojis.all(), emojis.search(""));
        assertEquals(emojis.all(), emojis.search(null));
    }

    @Test
    void bundledListIsWellFormed() {
        List<Entry> all = Emojis.load().all();
        assertTrue(all.size() >= 40, () -> "expected a decent curated list, got " + all.size());
        for (Entry e : all) {
            assertTrue(!e.glyph().isBlank(), () -> "blank glyph: " + e);
            assertTrue(!e.keywords().isEmpty(), () -> "no keywords: " + e);
            assertTrue(e.glyph().codePoints().noneMatch(cp -> cp == 0xFE0F),
                    () -> "variation selector U+FE0F in " + e + " (see ADR-0004)");
        }
    }
}
