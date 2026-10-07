package net.timafe.triptale.audio;

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

class AudioLibraryTest {

    @TempDir
    Path tempDir;

    private Path audioDir;
    private AudioLibrary library;

    @BeforeEach
    void setUp() {
        TripTaleProperties props = new TripTaleProperties();
        props.setSettingsDir(tempDir.resolve("settings").toString());
        SettingsStore settingsStore = new SettingsStore(props);
        Path dataDir = tempDir.resolve("data");
        AppSettings settings = new AppSettings();
        settings.setDataDir(dataDir.toString());
        settingsStore.save(settings);
        library = new AudioLibrary(new MarkdownStore(settingsStore));
        audioDir = dataDir.resolve("audio");
    }

    @Test
    void listTracks_emptyWhenFolderMissing() {
        assertEquals(List.of(), library.listTracks());
    }

    @Test
    void listTracks_recursiveSortedAndFiltersByExtension() throws Exception {
        Files.createDirectories(audioDir.resolve("Rock"));
        Files.writeString(audioDir.resolve("b.MP3"), "");
        Files.writeString(audioDir.resolve("Rock/Motörhead - Ace of Spades.mp3"), "");
        Files.writeString(audioDir.resolve("a.m4a"), "");
        Files.writeString(audioDir.resolve("Europe_-_Scandinavian_Eyes.mp3"), "");
        Files.writeString(audioDir.resolve("cover.jpg"), "");
        Files.writeString(audioDir.resolve("song.flac"), "");
        Files.writeString(audioDir.resolve(".gitignore"), "");
        Files.writeString(audioDir.resolve("._b.mp3"), "");

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
        Files.createDirectories(audioDir);
        Files.writeString(audioDir.resolve("one.mp3"), "");
        Track one = library.listTracks().getFirst();
        assertEquals(one, library.random(new Random(1), one).orElseThrow());

        Files.writeString(audioDir.resolve("two.mp3"), "");
        Random rnd = new Random(42);
        for (int i = 0; i < 20; i++) {
            assertEquals("two.mp3", library.random(rnd, one).orElseThrow().relative());
        }
    }

    @Test
    void ensureManagedFiles_writesGitignoreOnce() throws Exception {
        assertTrue(library.ensureManagedFiles());
        assertEquals(AudioLibrary.GITIGNORE, Files.readString(audioDir.resolve(".gitignore")));
        assertFalse(library.ensureManagedFiles());
    }
}
