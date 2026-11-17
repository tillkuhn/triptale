package net.timafe.triptale.impressions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.timafe.triptale.domain.Link;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads the optional JSON sidecar of a recorded tour (todo 94). Only the tour's text is
 * extracted: {@code tour.description} if non-blank, otherwise all comments written by the tour's
 * owner, oldest first, joined by a blank line. Comments by other people are skipped. Never
 * throws: an unreadable or unexpected file simply yields no text. No JavaFX imports.
 */
public final class TourSidecar {

    private static final ObjectMapper JSON = new ObjectMapper();

    private TourSidecar() {}

    public static Optional<String> tourText(Path json) {
        try {
            return tourText(JSON.readTree(Files.readString(json)));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** The tour's {@code web_url} as a named link ({@code "Komoot Tour 123"}), if present and a valid http(s) URL. */
    public static Optional<Link> webLink(Path json) {
        try {
            return webLink(JSON.readTree(Files.readString(json)));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    static Optional<Link> webLink(JsonNode root) {
        String url = root.path("web_url").asText("").strip();
        if (!Link.isHttpUrl(url)) return Optional.empty();
        return Optional.of(Link.of(url, nameFor(url)));
    }

    /** Link name from the URL: host without {@code www.} and top-level domain, then the path segments, capitalized. */
    static String nameFor(String url) {
        URI uri = URI.create(url);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.startsWith("www.")) host = host.substring(4);
        List<String> words = new ArrayList<>(List.of(host.split("\\.")));
        if (words.size() > 1) words.removeLast();
        if (uri.getPath() != null) {
            for (String seg : uri.getPath().split("/")) if (!seg.isBlank()) words.add(seg);
        }
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    static Optional<String> tourText(JsonNode root) {
        JsonNode tour = root.path("tour");
        String description = tour.path("description").asText("").strip();
        if (!description.isEmpty()) return Optional.of(description);

        String owner = tour.path("_embedded").path("creator").path("username").asText("");
        List<String> own = new ArrayList<>();
        for (JsonNode comment : root.path("comments")) {
            String author = comment.path("_embedded").path("creator").path("username").asText("");
            String text = comment.path("text").asText("").strip();
            if (!owner.isEmpty() && owner.equals(author) && !text.isEmpty()) own.add(text);
        }
        return own.isEmpty() ? Optional.empty() : Optional.of(String.join("\n\n", own));
    }
}
