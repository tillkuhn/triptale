package net.timafe.triptale.radio;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StationTest {

    @Test
    void parseSkipsBlankLinesAndComments() {
        assertEquals(List.of("A", "B"), Station.parse(List.of("# header", "", "  A  ", "   ", "B")));
    }

    @Test
    void djLineOnEmptyListIsEmpty() {
        assertTrue(new Station(List.of()).djLine(new Random(1), null).isEmpty());
    }

    @Test
    void djLineAvoidsPrevious() {
        Station station = new Station(List.of("A", "B"));
        for (int i = 0; i < 20; i++) {
            assertEquals("B", station.djLine(new Random(i), "A").orElseThrow());
        }
    }

    @Test
    void bundledLinesAreWellFormed() {
        List<String> all = Station.load().djLines();
        assertFalse(all.isEmpty());
        for (String line : all) {
            assertTrue(line.length() <= 60, () -> "too long for the status line: " + line);
            assertFalse(line.contains(Station.NAME), () -> "the status line already names the station: " + line);
        }
    }
}
