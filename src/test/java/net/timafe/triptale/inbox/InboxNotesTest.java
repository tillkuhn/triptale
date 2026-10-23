package net.timafe.triptale.inbox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InboxNotesTest {

    @Test
    void addsHeadingOnFirstNote() {
        assertEquals("## Inbox\n\nSaw a waterfall", InboxNotes.append("", "Saw a waterfall"));
    }

    @Test
    void appendsAfterExistingBodyWithBlankLine() {
        assertEquals("# Day 3\n\nIt rained all day.\n\n## Inbox\n\nSaw a waterfall",
                InboxNotes.append("# Day 3\n\nIt rained all day.", "Saw a waterfall"));
    }

    @Test
    void reusesExistingHeadingInsteadOfAddingASecondOne() {
        String body = "# Day 3\n\n## Inbox\n\nFirst note";
        assertEquals("# Day 3\n\n## Inbox\n\nFirst note\n\nSecond note",
                InboxNotes.append(body, "Second note"));
    }

    @Test
    void ignoresTrailingWhitespaceInExistingBody() {
        assertEquals("Some tale\n\n## Inbox\n\nNote", InboxNotes.append("Some tale\n\n  \n", "Note"));
    }
}
