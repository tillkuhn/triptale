package net.timafe.triptale.domain;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivityTest {

    @Test
    void declaredAlphabeticallyByKey() {
        List<String> keys = Arrays.stream(Activity.values()).map(Activity::key).toList();
        assertEquals(keys.stream().sorted().toList(), keys);
    }

    @Test
    void ofIgnoresCaseAndWhitespace() {
        assertEquals(Activity.HIKING, Activity.of(" Hiking ").orElseThrow());
        assertTrue(Activity.of("kayaking").isEmpty());
        assertTrue(Activity.of(null).isEmpty());
    }

    @Test
    void normalize() {
        assertEquals("hiking", Activity.normalize("HIKING"));
        assertEquals("Kayaking", Activity.normalize(" Kayaking "));
        assertNull(Activity.normalize("  "));
        assertNull(Activity.normalize(null));
    }

    @Test
    void emojiAndLabel() {
        assertEquals("🚴", Activity.emojiFor("cycling"));
        assertEquals(Activity.UNKNOWN_EMOJI, Activity.emojiFor("kayaking"));
        assertEquals("Roadtripping", Activity.ROADTRIPPING.label());
        assertEquals("Kayaking", Activity.labelFor("kayaking"));
    }

    @Test
    void builderNormalizes() {
        assertEquals("cycling", DiaryEntry.builder(java.time.LocalDate.of(2026, 7, 1)).activity(" Cycling").build().activity());
        assertNull(DiaryEntry.empty(java.time.LocalDate.of(2026, 7, 1)).activity());
    }
}
