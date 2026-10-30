package net.timafe.triptale.inbox;

import net.timafe.triptale.domain.LinkKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InboxNotesTest {

    private static final String SUFFIX = " — added by Octobot \uD83D\uDC19";

    @Test
    void createsPrivateSectionOnFirstNote() {
        assertEquals("## Private Notes \uD83D\uDD12\n\n* Saw a waterfall" + SUFFIX,
                InboxNotes.append("", "Saw a waterfall"));
    }

    @Test
    void appendsNewPrivateSectionAfterExistingBodyWithBlankLine() {
        assertEquals("# Day 3\n\nIt rained all day.\n\n## Private Notes \uD83D\uDD12\n\n* Saw a waterfall" + SUFFIX,
                InboxNotes.append("# Day 3\n\nIt rained all day.", "Saw a waterfall"));
    }

    @Test
    void stacksBulletsTightInsideExistingPrivateBullets() {
        String body = "# Day 3\n\n## Private Notes \uD83D\uDD12\n\n* First note" + SUFFIX;
        assertEquals("# Day 3\n\n## Private Notes \uD83D\uDD12\n\n* First note" + SUFFIX
                        + "\n* Second note" + SUFFIX,
                InboxNotes.append(body, "Second note"));
    }

    @Test
    void addsBlankLineBeforeBulletAfterFreeformPrivateContent() {
        String body = "# Day 3\n\n## Internal\n\nKomoot said 87 km.";
        assertEquals("# Day 3\n\n## Internal\n\nKomoot said 87 km.\n\n* Saw a waterfall" + SUFFIX,
                InboxNotes.append(body, "Saw a waterfall"));
    }

    @Test
    void insertsInsideBlockWhenPrivateSectionIsNotLast() {
        String body = "Intro\n\n## Internal\n\nsecret\n\n## Evening\n\npublic";
        assertEquals("Intro\n\n## Internal\n\nsecret\n\n* Saw a waterfall" + SUFFIX + "\n\n## Evening\n\npublic",
                InboxNotes.append(body, "Saw a waterfall"));
    }

    @Test
    void collapsesMultilineNoteIntoOneBullet() {
        String note = "Nice Cooking School\nhttps://asia.cooking.school/\nHas good ratings";
        assertEquals("## Private Notes \uD83D\uDD12\n\n* Nice Cooking School https://asia.cooking.school/ Has good ratings" + SUFFIX,
                InboxNotes.append("", note));
    }

    @Test
    void ignoresTrailingWhitespaceInExistingBody() {
        assertEquals("Some tale\n\n## Private Notes \uD83D\uDD12\n\n* Note" + SUFFIX,
                InboxNotes.append("Some tale\n\n  \n", "Note"));
    }

    @Test
    void namesTheVerbWhenAWeblinkWasAlsoCreated() {
        assertEquals("## Private Notes \uD83D\uDD12\n\n* Beldibi Turkish Bath — added and eat-linked by Octobot \uD83D\uDC19",
                InboxNotes.append("", "Beldibi Turkish Bath", LinkKind.EAT));
    }

    @Test
    void fallsBackToPlainSuffixWhenNoVerbIsGiven() {
        assertEquals("## Private Notes \uD83D\uDD12\n\n* Saw a waterfall" + SUFFIX,
                InboxNotes.append("", "Saw a waterfall", null));
    }
}
