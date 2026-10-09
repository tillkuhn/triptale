package net.timafe.triptale.impressions;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Where a day's impressions (images) come from. Plain values — resolved by
 * {@link ImpressionsService} — so they can sit directly in a combo box.
 */
public sealed interface ImpressionSource {

    ImpressionSource PHOTO_LIBRARY = new PhotoLibrary();
    ImpressionSource TRIP_ATTACHMENTS = new TripAttachments();

    String label();

    /** The machine-local photo library, located via the {@code impressionsFilePattern} setting. */
    record PhotoLibrary() implements ImpressionSource {
        @Override public String label() { return "Local Photo Lib"; }
    }

    /** The entry day's folder under {@code attachments/} (todo 33a). */
    record TripAttachments() implements ImpressionSource {
        @Override public String label() { return "Trip Attachments"; }
    }

    /** An arbitrary folder picked ad hoc, used for importing; the same folder for every date. */
    record Folder(Path dir) implements ImpressionSource {
        public Folder {
            Objects.requireNonNull(dir, "dir");
        }
        @Override public String label() { return "Pick Folder"; }
    }
}
