package net.timafe.triptale.attachments;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InlineImagesTest {

    @TempDir
    Path dataDir;

    private Path image;

    @BeforeEach
    void setUp() throws IOException {
        Path dir = Files.createDirectories(dataDir.resolve("attachments/2026/trip/2026-06-04-Thursday"));
        image = Files.createFile(dir.resolve("20260604_154300_TillBikeVidovaGora+_mini.jpg"));
        Files.createFile(dir.resolve("a (b).jpg"));
    }

    private static final String REF = "attachments/2026/trip/2026-06-04-Thursday/20260604_154300_TillBikeVidovaGora+_mini.jpg";

    @Test
    void altTextFromFileName() {
        assertEquals("Till Bike Vidova Gora", InlineImages.altText(image));
        assertEquals("IMG 1234", InlineImages.altText(Path.of("IMG_1234.JPG")));
    }

    @Test
    void markdownLineIsRelativeToTheDataDirAndEscapesSpacesAndParentheses() {
        assertEquals("![Till Bike Vidova Gora](" + REF + ")", InlineImages.markdownLine(dataDir, image));
        Path odd = dataDir.resolve("attachments/2026/trip/2026-06-04-Thursday/a (b).jpg");
        assertEquals("![a (b)](attachments/2026/trip/2026-06-04-Thursday/a%20%28b%29.jpg)",
                InlineImages.markdownLine(dataDir, odd));
        assertEquals(odd, InlineImages.resolve(dataDir, "attachments/2026/trip/2026-06-04-Thursday/a%20%28b%29.jpg").orElseThrow());
    }

    @Test
    void resolveOnlyAcceptsExistingFilesBelowAttachments() {
        assertTrue(InlineImages.resolve(dataDir, REF).isPresent());
        assertTrue(InlineImages.resolve(dataDir, "attachments/missing.jpg").isEmpty());
        assertTrue(InlineImages.resolve(dataDir, "attachments/../README.md").isEmpty());
        assertTrue(InlineImages.resolve(dataDir, "2026/trip/README.md").isEmpty());
        assertTrue(InlineImages.resolve(dataDir, "https://example.com/a.jpg").isEmpty());
    }

    @Test
    void blockWithEmptyLinesAroundIsRendered() {
        String md = "Intro\n\n![Pic](" + REF + ")\n\nOutro";
        var result = InlineImages.renderBlocks(md, dataDir, (alt, file) -> "[" + alt + "|" + file.getFileName() + "]");
        assertEquals("Intro\n\n[Pic|20260604_154300_TillBikeVidovaGora+_mini.jpg]\n\nOutro", result.text());
        assertTrue(result.problems().isEmpty());
    }

    @Test
    void blockAtStartAndEndOfTextAndEmptyAltUsesTheFileName() {
        var result = InlineImages.renderBlocks("![](" + REF + ")", dataDir, (alt, file) -> alt);
        assertEquals("Till Bike Vidova Gora", result.text());
    }

    @Test
    void withoutEmptyLinesOrInsideASentenceItStaysText() {
        String touching = "Text\n![Pic](" + REF + ")\nmore";
        var result = InlineImages.renderBlocks(touching, dataDir, (alt, file) -> "X");
        assertEquals(touching, result.text());
        assertEquals(1, result.problems().size());

        String sentence = "See ![Pic](" + REF + ") here";
        result = InlineImages.renderBlocks("\n" + sentence + "\n", dataDir, (alt, file) -> "X");
        assertEquals("\n" + sentence + "\n", result.text());
        assertEquals(1, result.problems().size());
    }

    @Test
    void missingFileIsDroppedAndForeignPathStaysAndBothAreReported() {
        String md = "A\n\n![Gone](attachments/nope.jpg)\n\n![Web](https://example.com/a.jpg)\n\nB";
        var result = InlineImages.renderBlocks(md, dataDir, (alt, file) -> "X");
        assertEquals("A\n\n\n![Web](https://example.com/a.jpg)\n\nB", result.text());
        assertEquals(2, result.problems().size());
    }

    @Test
    void fencedCodeIsLeftAlone() {
        String md = "```\n\n![Pic](" + REF + ")\n\n```";
        var result = InlineImages.renderBlocks(md, dataDir, (alt, file) -> "X");
        assertEquals(md, result.text());
        assertTrue(result.problems().isEmpty());
    }

    @Test
    void referencedCollectsTheFilesOfValidBlocks() {
        assertEquals(java.util.Set.of(image), InlineImages.referenced("x\n\n![P](" + REF + ")\n\n![Q](attachments/nope.jpg)", dataDir));
        assertTrue(InlineImages.referenced(null, dataDir).isEmpty());
    }
}
