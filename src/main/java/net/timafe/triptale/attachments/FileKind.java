package net.timafe.triptale.attachments;

import java.util.Locale;
import java.util.Set;

/**
 * Rough kind of a backpack file for the backpack dialog's Kind column (todo 56). Mapped from the
 * file extension only — MIME probing ({@code Files.probeContentType}) differs per OS.
 *
 * <p>No JavaFX imports (package boundary rule).
 */
public enum FileKind {

    IMAGE("🖼", "Image", Set.of("jpg", "jpeg", "png", "heic", "webp", "gif")),
    TRACK("🗺", "Track", Set.of("gpx", "fit", "kml", "tcx")),
    TEXT("📄", "Text", Set.of("txt", "md")),
    PDF("📕", "PDF", Set.of("pdf")),
    VIDEO("🎬", "Video", Set.of("mov", "mp4")),
    OTHER("📦", "Other", Set.of());

    private final String emoji;
    private final String label;
    private final Set<String> extensions;

    FileKind(String emoji, String label, Set<String> extensions) {
        this.emoji = emoji;
        this.label = label;
        this.extensions = extensions;
    }

    public String emoji() {
        return emoji;
    }

    public String label() {
        return label;
    }

    /** "🖼 Image" */
    public String display() {
        return emoji + " " + label;
    }

    /** The kind for a file name, case-insensitive on the extension; {@link #OTHER} if none matches. */
    public static FileKind of(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return OTHER;
        String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        for (FileKind kind : values()) {
            if (kind.extensions.contains(ext)) return kind;
        }
        return OTHER;
    }
}
