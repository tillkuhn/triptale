package net.timafe.triptale.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ByteSizesTest {

    @Test
    void humanize_picksUnit() {
        assertEquals("1 KB", ByteSizes.humanize(0));
        assertEquals("1 KB", ByteSizes.humanize(400));
        assertEquals("312 KB", ByteSizes.humanize(312_000));
        assertEquals("4.5 MB", ByteSizes.humanize(4_500_000));
        assertEquals("1.2 GB", ByteSizes.humanize(1_234_000_000L));
    }

    @Test
    void humanize_rejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> ByteSizes.humanize(-1));
    }
}
