package net.timafe.triptale.radio;

import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.config.TripTaleProperties;
import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.storage.SettingsStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class RadioLibraryTest {

    @TempDir
    Path tempDir;

    private Path radioDir;
    private RadioLibrary library;

    @BeforeEach
    void setUp() {
        TripTaleProperties props = new TripTaleProperties();
        props.setSettingsDir(tempDir.resolve("settings").toString());
        SettingsStore settingsStore = new SettingsStore(props);
        Path dataDir = tempDir.resolve("data");
        AppSettings settings = new AppSettings();
        settings.setDataDir(dataDir.toString());
        settingsStore.save(settings);
        library = new RadioLibrary(new MarkdownStore(settingsStore));
        radioDir = dataDir.resolve("radio");
    }

    @Test
    void listTracks_emptyWhenFolderMissing() {
        assertEquals(List.of(), library.listTracks());
    }

    @Test
    void listTracks_recursiveSortedAndFiltersByExtension() throws Exception {
        Files.createDirectories(radioDir.resolve("Rock"));
        Files.writeString(radioDir.resolve("b.MP3"), "");
        Files.writeString(radioDir.resolve("Rock/Motörhead - Ace of Spades.mp3"), "");
        Files.writeString(radioDir.resolve("a.m4a"), "");
        Files.writeString(radioDir.resolve("Europe_-_Scandinavian_Eyes.mp3"), "");
        Files.writeString(radioDir.resolve("cover.jpg"), "");
        Files.writeString(radioDir.resolve("song.flac"), "");
        Files.writeString(radioDir.resolve(".gitignore"), "");
        Files.writeString(radioDir.resolve("._b.mp3"), "");

        List<Track> tracks = library.listTracks();

        assertEquals(List.of("a.m4a", "b.MP3", "Europe_-_Scandinavian_Eyes.mp3",
                        "Rock/Motörhead - Ace of Spades.mp3"),
                tracks.stream().map(Track::relative).toList());
        assertEquals("Europe - Scandinavian Eyes", tracks.get(2).title());
        assertEquals("Motörhead - Ace of Spades", tracks.get(3).title());
    }

    @Test
    void random_emptyWithoutTracks() {
        assertTrue(library.random(new Random(1), null).isEmpty());
    }

    @Test
    void random_neverRepeatsPreviousUnlessOnlyTrack() throws Exception {
        Files.createDirectories(radioDir);
        Files.writeString(radioDir.resolve("one.mp3"), "");
        Track one = library.listTracks().getFirst();
        assertEquals(one, library.random(new Random(1), one).orElseThrow());

        Files.writeString(radioDir.resolve("two.mp3"), "");
        Random rnd = new Random(42);
        for (int i = 0; i < 20; i++) {
            assertEquals("two.mp3", library.random(rnd, one).orElseThrow().relative());
        }
    }

    @Test
    void ensureManagedFiles_writesGitignoreOnce() throws Exception {
        assertTrue(library.ensureManagedFiles());
        assertEquals(RadioLibrary.GITIGNORE, Files.readString(radioDir.resolve(".gitignore")));
        assertFalse(library.ensureManagedFiles());
    }
}
