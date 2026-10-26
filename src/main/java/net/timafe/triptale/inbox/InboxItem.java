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
 * its text (a caption, possibly empty, for a photo item — see {@link #photo()}), and the
 * downloaded photo if this came from a Telegram photo message (todo 74) rather than a text
 * message. No JavaFX imports (package boundary rule).
 */
public record InboxItem(Path file, LocalDateTime timestamp, String text, Optional<Path> photo) {

    static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

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
        try {
            String text = Files.readString(file);
            Path photoFile = file.resolveSibling(stem + ".jpg");
            Optional<Path> photo = Files.isRegularFile(photoFile) ? Optional.of(photoFile) : Optional.empty();
            return Optional.of(new InboxItem(file, timestamp, text, photo));
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
