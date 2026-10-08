package net.timafe.triptale.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomPickTest {

    @Test
    void emptyListYieldsEmpty() {
        assertTrue(RandomPick.from(List.of(), new Random(1), null).isEmpty());
    }

    @Test
    void singleElementIsRepeated() {
        assertEquals("a", RandomPick.from(List.of("a"), new Random(1), "a").orElseThrow());
    }

    @Test
    void avoidsPrevious() {
        Random rnd = new Random(3);
        for (int i = 0; i < 50; i++) {
            assertNotEquals("b", RandomPick.from(List.of("a", "b", "c"), rnd, "b").orElseThrow());
        }
    }
}
