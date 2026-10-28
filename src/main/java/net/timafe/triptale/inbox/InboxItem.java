package net.timafe.triptale.inbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * One pending mobile-inbox capture (todo 68): the file it lives in, when the message was sent,
 * its text (a caption, possibly empty, for a photo item — see {@link #photo()}), and the
 * downloaded photo if this came from a Telegram photo message (todo 74) rather than a text
 * message. {@code messageId}/{@code fromName} (todo 75) are read back from the same-stem
 * {@code .json} raw-payload sidecar (todo 73) when present — Telegram's {@code message_id} and
 * the sender's {@code first_name [last_name]} — so the Inbox dialog can show "who sent this" in
 * preparation for more than one family member capturing into the same inbox; absent (not Telegram,
 * or no sidecar) for anything else that might land in {@code inbox/} one day. No JavaFX imports
 * (package boundary rule).
 */
public record InboxItem(Path file, LocalDateTime timestamp, String text, Optional<Path> photo,
                         OptionalLong messageId, Optional<String> fromName) {

    static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Parses the filename's leading {@code yyyyMMdd-HHmmss} (an optional {@code -N} collision
     * suffix before {@code .txt} is ignored), reads the file's content as the note/caption text,
     * and checks for a same-stem {@code .jpg} sidecar (todo 74); empty if the name doesn't parse
     * or the {@code .txt} can't be read.
     */
    public static Optional<InboxItem> parse(Path file) {
        String stem = file.getFileName().toString().replaceFirst("\\.txt$", "");
        String stampPart = stem.length() >= 15 ? stem.substring(0, 15) : stem;
        LocalDateTime timestamp;
        try {
            timestamp = LocalDateTime.parse(stampPart, FILE_STAMP);
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
        String text;
        try {
            text = Files.readString(file);
        } catch (IOException e) {
            return Optional.empty();
        }
        Path photoFile = file.resolveSibling(stem + ".jpg");
        Optional<Path> photo = Files.isRegularFile(photoFile) ? Optional.of(photoFile) : Optional.empty();
        OptionalLong messageId = OptionalLong.empty();
        Optional<String> fromName = Optional.empty();
        Path jsonFile = file.resolveSibling(stem + ".json");
        if (Files.isRegularFile(jsonFile)) {
            try {
                JsonNode message = JSON.readTree(Files.readString(jsonFile)).path("message");
                if (message.has("message_id")) messageId = OptionalLong.of(message.path("message_id").asLong());
                fromName = fromName(message.path("from"));
            } catch (IOException ignored) {
                // Sidecar unreadable/corrupt — still show the item, just without the extra metadata.
            }
        }
        return Optional.of(new InboxItem(file, timestamp, text, photo, messageId, fromName));
    }

    /** "First Last" (or just "First"/"Last") from a Telegram {@code from} object; empty if neither name is present. */
    private static Optional<String> fromName(JsonNode from) {
        String first = from.path("first_name").asText("");
        String last = from.path("last_name").asText("");
        String combined = (first + " " + last).trim();
        return combined.isBlank() ? Optional.empty() : Optional.of(combined);
    }
}
