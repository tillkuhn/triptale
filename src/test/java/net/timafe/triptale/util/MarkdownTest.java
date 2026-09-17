package net.timafe.triptale.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
