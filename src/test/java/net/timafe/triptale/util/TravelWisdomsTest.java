package net.timafe.triptale.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TravelWisdomsTest {

    @Test
    void parseSkipsBlankLinesAndComments() {
        List<String> parsed = TravelWisdoms.parse(List.of("# header", "", "  A — B  ", "   ", "C — D"));
        assertEquals(List.of("A — B", "C — D"), parsed);
    }

    @Test
    void randomOnEmptyListIsEmpty() {
        assertTrue(new TravelWisdoms(List.of()).random(new Random(1), null).isEmpty());
    }

    @Test
    void randomPicksFromList() {
        TravelWisdoms wisdoms = new TravelWisdoms(List.of("A — B", "C — D"));
        String pick = wisdoms.random(new Random(42), null).orElseThrow();
        assertTrue(wisdoms.all().contains(pick));
    }

    @Test
    void bundledListIsWellFormed() {
        List<String> all = TravelWisdoms.load().all();
        assertEquals(25, all.size());
        for (String w : all) {
            assertTrue(w.contains(" — "), () -> "missing ' — author': " + w);
            assertTrue(w.length() <= 70, () -> "too long for the title bar: " + w);
        }
    }
}
