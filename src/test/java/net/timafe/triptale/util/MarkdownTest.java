package net.timafe.triptale.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class MarkdownTest {

    @Test
    void demoteH1DemotesExactH1LineToH2() {
        assertEquals("## Foo\ntext", Markdown.demoteH1("# Foo\ntext"));
    }

    @Test
    void demoteH1LeavesExistingH2Untouched() {
        assertEquals("## Foo\ntext", Markdown.demoteH1("## Foo\ntext"));
    }

    @Test
    void demoteH1LeavesExistingH3Untouched() {
        assertEquals("### Foo\ntext", Markdown.demoteH1("### Foo\ntext"));
    }

    @Test
    void demoteH1DemotesMultipleLinesInOneBody() {
        String body = "# One\ntext\n\n# Two\nmore text";
        assertEquals("## One\ntext\n\n## Two\nmore text", Markdown.demoteH1(body));
    }

    @Test
    void demoteH1LeavesBareHashWithoutSpaceUntouched() {
        assertEquals("#\ntext", Markdown.demoteH1("#\ntext"));
    }

    @Test
    void demoteH1HandlesNullAndEmpty() {
        assertNull(Markdown.demoteH1(null));
        assertEquals("", Markdown.demoteH1(""));
    }

    @Test
    void extractTitleSplitsFirstLineAndRemainder() {
        Markdown.TitleAndBody result = Markdown.extractTitle("# From Vechta to Bremen\n\nTough climb.");
        assertEquals("From Vechta to Bremen", result.title());
        assertEquals("Tough climb.", result.remainder());
    }

    @Test
    void extractTitleToleratesMissingBlankLineAfterTitle() {
        Markdown.TitleAndBody result = Markdown.extractTitle("# Title\nBody starts immediately.");
        assertEquals("Title", result.title());
        assertEquals("Body starts immediately.", result.remainder());
    }

    @Test
    void extractTitleReturnsNullTitleWhenBodyHasNoH1() {
        Markdown.TitleAndBody result = Markdown.extractTitle("Just some text.");
        assertNull(result.title());
        assertEquals("Just some text.", result.remainder());
    }

    @Test
    void extractTitleTrimsTrailingWhitespaceFromTitleLine() {
        Markdown.TitleAndBody result = Markdown.extractTitle("#  Spacey Title  \nrest");
        assertEquals("Spacey Title", result.title());
        assertEquals("rest", result.remainder());
    }

    @Test
    void shiftHeadingsShiftsH2ToH3() {
        assertEquals("### Foo", Markdown.shiftHeadings("## Foo", 1));
    }

    @Test
    void shiftHeadingsShiftsH1ToH2() {
        assertEquals("## Foo", Markdown.shiftHeadings("# Foo", 1));
    }

    @Test
    void shiftHeadingsClampsAtH6() {
        assertEquals("###### Foo", Markdown.shiftHeadings("###### Foo", 1));
    }

    @Test
    void shiftHeadingsLeavesSevenHashesUntouched() {
        assertEquals("####### Foo", Markdown.shiftHeadings("####### Foo", 1));
    }

    @Test
    void shiftHeadingsShiftsMultipleHeadingsIndependently() {
        String body = "# A\ntext\n## B\nmore\n### C";
        assertEquals("## A\ntext\n### B\nmore\n#### C", Markdown.shiftHeadings(body, 1));
    }

    @Test
    void shiftHeadingsLeavesNonHeadingLinesUntouched() {
        String body = "plain text\ntext with # not at start\n#";
        assertEquals(body, Markdown.shiftHeadings(body, 1));
    }

    @Test
    void shiftHeadingsByZeroIsNoOp() {
        String body = "# A\ntext\n## B";
        assertEquals(body, Markdown.shiftHeadings(body, 0));
    }

    @Test
    void shiftHeadingsHandlesNullAndEmpty() {
        assertNull(Markdown.shiftHeadings(null, 1));
        assertEquals("", Markdown.shiftHeadings("", 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"## Internal", "### privat:", "## Private: costs", "## Internal notes",
            "## Intern: Strecke", "## Private beach at Kalamaki", "# INTERNAL", "###### private",
            "##   Internal  "})
    void stripPrivateRecognizesMarkerHeadings(String heading) {
        assertEquals("Public.", Markdown.stripPrivate("Public.\n\n" + heading + "\nsecret"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"## Private-Strand", "## Internals", "## Privatstrand", "## Private!",
            "## Notes: internal", "Internal", "#Internal", "####### Internal"})
    void stripPrivateIgnoresNonMarkerLines(String line) {
        String body = "Public.\n\n" + line + "\nstill public";
        assertSame(body, Markdown.stripPrivate(body));
    }

    @Test
    void stripPrivateEndsBlockAtSameOrHigherLevelHeading() {
        String body = "Intro\n\n## Internal\nsecret\n\n## Evening\npublic\n\n### Private\nx\n# Next day\ny";
        assertEquals("Intro\n\n## Evening\npublic\n\n# Next day\ny", Markdown.stripPrivate(body));
    }

    @Test
    void stripPrivateKeepsSubheadingsInsideBlock() {
        String body = "Intro\n\n## Internal\nsecret\n### Distances\n87 km\n#### Detail\nmore";
        assertEquals("Intro", Markdown.stripPrivate(body));
    }

    @Test
    void stripPrivateIgnoresHeadingsInCodeFences() {
        String fenced = "Intro\n```\n## Internal\n```\nafter";
        assertSame(fenced, Markdown.stripPrivate(fenced));
        // A fenced "# heading" inside a private block does not end it
        String inside = "Intro\n\n## Private\n~~~\n# Not a heading\n~~~\nsecret";
        assertEquals("Intro", Markdown.stripPrivate(inside));
    }

    @Test
    void stripPrivateOfEntirelyPrivateTaleIsBlank() {
        assertEquals("", Markdown.stripPrivate("## Internal\n\nall secret\n"));
    }

    @Test
    void stripPrivateHandlesNullAndEmpty() {
        assertNull(Markdown.stripPrivate(null));
        assertEquals("", Markdown.stripPrivate(""));
    }

    @Test
    void privateHeadingOffsetFindsFirstMarkerOutsideFences() {
        String body = "Intro\n```\n## Internal\n```\n## Private: x\nsecret";
        assertEquals(body.indexOf("## Private"), Markdown.privateHeadingOffset(body));
        assertEquals(-1, Markdown.privateHeadingOffset("Intro\n## Internals"));
        assertEquals(-1, Markdown.privateHeadingOffset(null));
    }
}
