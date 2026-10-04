package net.timafe.triptale.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommitMessageTest {

    @Test
    void external_dropsFilesCoveredByPendingLabels() {
        List<String> dirty = List.of(
                "2026/norway/2026-06-04-Thursday.md",
                "2026/norway/README.md",
                "2026/norway-two/README.md",
                "attachments/.gitignore",
                "tale.md");
        List<String> labels = List.of("2026/norway/2026-06-04", "attachments/.gitignore");
        assertEquals(List.of("2026/norway/README.md", "2026/norway-two/README.md", "tale.md"),
                CommitMessage.external(labels, dirty));
    }

    @Test
    void external_tripLabelCoversWholeTripFolderButNotSiblingSlug() {
        assertEquals(List.of("2026/norway-two/README.md"),
                CommitMessage.external(List.of("2026/norway"),
                        List.of("2026/norway/README.md", "2026/norway-two/README.md")));
    }

    @Test
    void compose_pendingOnly() {
        assertEquals("Update: a", CommitMessage.compose("Update: a", List.of()));
    }

    @Test
    void compose_pendingPlusExternal() {
        assertEquals("Update: a | + 2 external changes",
                CommitMessage.compose("Update: a", List.of("x.md", "y.md")));
    }

    @Test
    void compose_externalOnlyListsFiles() {
        assertEquals("Sync: 1 external change: x.md", CommitMessage.compose("", List.of("x.md")));
    }

    @Test
    void compose_externalOnlyTruncatesLongLists() {
        assertEquals("Sync: 7 external changes: a, b, c, d, e, ... and 2 more",
                CommitMessage.compose(null, List.of("a", "b", "c", "d", "e", "f", "g")));
    }

    @Test
    void compose_nothingAtAll() {
        assertEquals("Sync", CommitMessage.compose("", List.of()));
    }
}
