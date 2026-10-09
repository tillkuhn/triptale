package net.timafe.triptale.impressions;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageFilterTest {

    @Test
    void baseThenFaveFilterReducesTheSet() {
        List<Path> files = List.of(Path.of("/x/a.jpg"), Path.of("/x/b.jpg"), Path.of("/x/c+.jpg"), Path.of("/x/d.txt"));

        List<Path> images = ImageFilter.parse("*.jpg *.jpeg *.png").apply(files);
        List<Path> faves = ImageFilter.parse("*+.???").apply(images);

        assertEquals(List.of(Path.of("/x/a.jpg"), Path.of("/x/b.jpg"), Path.of("/x/c+.jpg")), images);
        assertEquals(List.of(Path.of("/x/c+.jpg")), faves);
    }

    @Test
    void matchesCaseInsensitivelyOnFilenameOnly() {
        ImageFilter filter = ImageFilter.parse("*.jpg");
        assertTrue(filter.matches(Path.of("/Photos/IMG_0431.JPG")));
        assertFalse(filter.matches(Path.of("/photos.jpg/track.gpx")));
    }

    @Test
    void blankFilterIsEmptyAndMatchesEverything() {
        ImageFilter filter = ImageFilter.parse("  ");
        assertTrue(filter.isEmpty());
        assertTrue(filter.matches(Path.of("notes.txt")));
        assertTrue(ImageFilter.parse(null).isEmpty());
    }

    @Test
    void toleratesExtraWhitespaceBetweenGlobs() {
        ImageFilter filter = ImageFilter.parse(" *.png   *.jpg ");
        assertTrue(filter.matches(Path.of("a.png")));
        assertTrue(filter.matches(Path.of("a.jpg")));
        assertEquals("*.png   *.jpg", filter.toString());
    }
}
