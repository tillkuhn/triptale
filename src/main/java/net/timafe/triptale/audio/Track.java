package net.timafe.triptale.audio;

import java.nio.file.Path;

/**
 * One audio file in the data dir's {@code audio/} folder (todo 46).
 *
 * @param file     absolute path of the audio file
 * @param relative path relative to {@code audio/}, with {@code /} separators on every OS
 */
public record Track(Path file, String relative) {

    /**
     * The file name without extension, underscores as blanks, e.g. {@code "Motörhead - Ace of Spades"}
     * for {@code Motörhead_-_Ace_of_Spades.mp3}.
     */
    public String title() {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return (dot > 0 ? name.substring(0, dot) : name).replace('_', ' ').strip();
    }
}
