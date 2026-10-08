package net.timafe.triptale.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * "Welcome to TripTale" in various languages for the window title bar, read from the bundled
 * {@code greetings.txt} (one {@code Language | template} per line, {@code {app}} marks where the
 * app name goes; blank lines and {@code #} comments are skipped).
 */
public final class Greetings {

    static final String RESOURCE = "/greetings.txt";
    static final String APP_PLACEHOLDER = "{app}";

    /** One greeting; {@link #text(String)} fills in the app name. */
    public record Greeting(String language, String template) {
        public String text(String appName) {
            return template.replace(APP_PLACEHOLDER, appName);
        }
    }

    private final List<Greeting> greetings;

    Greetings(List<Greeting> greetings) {
        this.greetings = List.copyOf(greetings);
    }

    /** Loads the bundled list; a missing resource yields an empty list rather than an error. */
    public static Greetings load() {
        try (InputStream in = Greetings.class.getResourceAsStream(RESOURCE)) {
            if (in == null) return new Greetings(List.of());
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return new Greetings(parse(reader.lines().toList()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Lines without a {@code |} separator are skipped. */
    static List<Greeting> parse(List<String> lines) {
        return lines.stream()
                .map(String::strip)
                .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                .filter(l -> l.contains("|"))
                .map(l -> new Greeting(
                        l.substring(0, l.indexOf('|')).strip(),
                        l.substring(l.indexOf('|') + 1).strip()))
                .toList();
    }

    public List<Greeting> all() {
        return greetings;
    }

    /** A random greeting other than {@code previous} (if there is any other choice). */
    public Optional<Greeting> random(RandomGenerator rnd, Greeting previous) {
        return RandomPick.from(greetings, rnd, previous);
    }
}
