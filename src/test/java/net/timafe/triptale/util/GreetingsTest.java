package net.timafe.triptale.util;

import net.timafe.triptale.util.Greetings.Greeting;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GreetingsTest {

    @Test
    void parseSplitsLanguageAndTemplate() {
        List<Greeting> parsed = Greetings.parse(List.of(
                "# header", "", " Turkish | {app}'e Hoş Geldiniz ", "no separator", "German|Willkommen bei {app}"));
        assertEquals(List.of(
                new Greeting("Turkish", "{app}'e Hoş Geldiniz"),
                new Greeting("German", "Willkommen bei {app}")), parsed);
    }

    @Test
    void textFillsInAppName() {
        assertEquals("TripTale'e Hoş Geldiniz",
                new Greeting("Turkish", "{app}'e Hoş Geldiniz").text("TripTale"));
    }

    @Test
    void randomNeverRepeatsPrevious() {
        Greeting a = new Greeting("A", "a {app}");
        Greeting b = new Greeting("B", "b {app}");
        Greetings greetings = new Greetings(List.of(a, b));
        Random rnd = new Random(7);
        for (int i = 0; i < 20; i++) {
            assertEquals(b, greetings.random(rnd, a).orElseThrow());
        }
    }

    @Test
    void bundledListIsWellFormed() {
        List<Greeting> all = Greetings.load().all();
        assertTrue(all.size() >= 20, () -> "expected ~20 greetings, got " + all.size());
        assertTrue(all.stream().anyMatch(g -> g.language().equals("English")));
        for (Greeting g : all) {
            assertTrue(g.template().contains(Greetings.APP_PLACEHOLDER), () -> "missing {app}: " + g);
        }
        assertEquals(all.size(), all.stream().map(Greeting::language).distinct().count(),
                "duplicate language");
    }
}
