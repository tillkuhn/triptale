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
 * Random travel quotes for the window title bar, read from the bundled {@code wisdoms.txt}
 * (one {@code quote — author} per line; blank lines and {@code #} comments are skipped).
 */
public final class TravelWisdoms {

    static final String RESOURCE = "/wisdoms.txt";

    private final List<String> wisdoms;

    TravelWisdoms(List<String> wisdoms) {
        this.wisdoms = List.copyOf(wisdoms);
    }

    /** Loads the bundled list; a missing resource yields an empty list rather than an error. */
    public static TravelWisdoms load() {
        try (InputStream in = TravelWisdoms.class.getResourceAsStream(RESOURCE)) {
            if (in == null) return new TravelWisdoms(List.of());
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return new TravelWisdoms(parse(reader.lines().toList()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<String> parse(List<String> lines) {
        return lines.stream()
                .map(String::strip)
                .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                .toList();
    }

    public List<String> all() {
        return wisdoms;
    }

    public Optional<String> random(RandomGenerator rnd) {
        if (wisdoms.isEmpty()) return Optional.empty();
        return Optional.of(wisdoms.get(rnd.nextInt(wisdoms.size())));
    }
}
