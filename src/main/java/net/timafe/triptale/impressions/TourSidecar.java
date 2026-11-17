package net.timafe.triptale.impressions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
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
