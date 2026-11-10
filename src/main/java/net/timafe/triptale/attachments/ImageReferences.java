package net.timafe.triptale.attachments;

import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.storage.MarkdownStore;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Where a trip's Markdown places an attachment inline (todo 92), and the line to paste to place
 * one. Scans the trip's README, entries and Posts. No JavaFX imports (package boundary rule).
 */
public class ImageReferences {

    private final MarkdownStore store;

    public ImageReferences(MarkdownStore store) {
        this.store = store;
    }

    /** {@code ![Name](attachments/...)} for {@code file}, a file below the data dir. */
    public String markdownLine(Path file) {
        return InlineImages.markdownLine(store.dataDir(), file);
    }

    /** Human-readable names of the trip's texts that place {@code file} inline, empty if none. */
    public List<String> referencingTexts(Trip trip, Path file) {
        Path dataDir = store.dataDir();
        Path target = file.normalize();
        List<String> found = new ArrayList<>();
        if (references(trip.description(), dataDir, target)) found.add("Trip README");
        for (LocalDate date : store.listEntryDates(trip.ref())) {
            if (references(store.loadEntry(trip.ref(), date).tales(), dataDir, target)) {
                found.add(store.entryFile(trip.ref(), date).getFileName().toString());
            }
        }
        store.listPosts(trip.ref()).forEach(post -> {
            if (references(post.intro(), dataDir, target)) found.add("Post " + post.slug());
        });
        return found;
    }

    private static boolean references(String markdown, Path dataDir, Path target) {
        return InlineImages.referenced(markdown, dataDir).contains(target);
    }
}
