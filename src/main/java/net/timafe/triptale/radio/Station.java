package net.timafe.triptale.radio;

import net.timafe.triptale.util.RandomPick;

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
 * The radio's on-air branding: the station name shown in the UI, and the DJ lines read from
 * the bundled {@code dj-lines.txt} (one per line; blank lines and {@code #} comments are
 * skipped). Branding only — code, folder and settings keep calling it "radio".
 */
public final class Station {

    public static final String NAME = "Tailwind FM";

    static final String RESOURCE = "/dj-lines.txt";
    static final String NAMES_RESOURCE = "/dj-names.txt";
    /** Used when there are no bundled DJ names. */
    static final String DEFAULT_DJ = "DJ";

    private final List<String> lines;
    private final List<String> djNames;

    Station(List<String> lines) {
        this(lines, List.of());
    }

    Station(List<String> lines, List<String> djNames) {
        this.lines = List.copyOf(lines);
        this.djNames = List.copyOf(djNames);
    }

    /** Loads the bundled DJ lines and names; a missing resource yields none rather than an error. */
    public static Station load() {
        return new Station(readResource(RESOURCE), readResource(NAMES_RESOURCE));
    }

    private static List<String> readResource(String resource) {
        try (InputStream in = Station.class.getResourceAsStream(resource)) {
            if (in == null) return List.of();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return parse(reader.lines().toList());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<String> parse(List<String> raw) {
        return raw.stream()
                .map(String::strip)
                .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                .toList();
    }

    public List<String> djLines() {
        return lines;
    }

    public List<String> djNames() {
        return djNames;
    }

    /** A random DJ name like "DJ Backflip", or plain "DJ" if there are none. */
    public String djName(RandomGenerator rnd) {
        return RandomPick.from(djNames, rnd, null).orElse(DEFAULT_DJ);
    }

    /** A random DJ line other than {@code previous} (if there is any other choice). */
    public Optional<String> djLine(RandomGenerator rnd, String previous) {
        return RandomPick.from(lines, rnd, previous);
    }
}
