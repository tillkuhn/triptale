package net.timafe.triptale.inbox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * One pending mobile-inbox capture (todo 68): the file it lives in, when the message was sent,
 * and its text. No JavaFX imports (package boundary rule).
 */
public record InboxItem(Path file, LocalDateTime timestamp, String text) {

    static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /**
     * Parses the filename's leading {@code yyyyMMdd-HHmmss} (an optional {@code -N} collision
     * suffix before {@code .txt} is ignored) and reads the file's content as the note text;
     * empty if either the name doesn't parse or the file can't be read.
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
        try {
            return Optional.of(new InboxItem(file, timestamp, Files.readString(file)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
