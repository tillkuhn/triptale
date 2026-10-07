package net.timafe.triptale.ui;

import javafx.collections.MapChangeListener;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import net.timafe.triptale.radio.RadioLibrary;
import net.timafe.triptale.radio.Track;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Plays random tracks from {@link RadioLibrary} (todo 46) — a thin wrapper around JavaFX
 * {@link MediaPlayer}; the track selection logic stays in the JavaFX-free {@code radio} package.
 *
 * <p>Created lazily by {@code MainController} on first use, so {@code javafx.media} (and its
 * native libraries) is never loaded at startup. All methods run on the FX thread; MediaPlayer
 * callbacks arrive there too.
 */
public class RadioPlayer {

    private static final Logger log = LoggerFactory.getLogger(RadioPlayer.class);

    private final RadioLibrary library;
    private final StatusSink statusSink;
    private final Runnable onChange;

    /** Held in a field on purpose: a MediaPlayer nothing references is garbage-collected mid-track. */
    private MediaPlayer player;
    private Track current;

    /**
     * @param onChange runs after every state change (track, play/pause, stop), e.g. to refresh
     *                 the toolbar button
     */
    public RadioPlayer(RadioLibrary library, StatusSink statusSink, Runnable onChange) {
        this.library = library;
        this.statusSink = statusSink;
        this.onChange = onChange;
    }

    /** Plays a random track (never the current one, unless it's the only one). */
    public void playRandom() {
        library.random(ThreadLocalRandom.current(), current).ifPresentOrElse(this::play, () -> {
            stop();
            statusSink.status("🎵 No tracks yet — put some MP3s into " + UiText.homeRelative(library.root()));
        });
    }

    /** Pauses or resumes; starts a random track if nothing is loaded. */
    public void togglePause() {
        if (player == null) {
            playRandom();
        } else if (isPlaying()) {
            player.pause();
            statusSink.status("🎵 Paused: " + label());
        } else {
            player.play();
            statusSink.status("🎵 Playing: " + label());
        }
        onChange.run();
    }

    public void stop() {
        disposePlayer();
        current = null;
        onChange.run();
    }

    public boolean isPlaying() {
        return player != null && player.getStatus() == MediaPlayer.Status.PLAYING;
    }

    public boolean isLoaded() {
        return player != null;
    }

    /** "Artist – Title" from the ID3 tags once known, else the file name; empty when stopped. */
    public String label() {
        if (current == null) return "";
        if (player != null) {
            var meta = player.getMedia().getMetadata();
            if (meta.get("title") instanceof String title && !title.isBlank()) {
                return meta.get("artist") instanceof String artist && !artist.isBlank()
                        ? artist + " – " + title : title;
            }
        }
        return current.title();
    }

    private void play(Track track) {
        disposePlayer();
        current = track;
        try {
            // toUri() percent-encodes spaces and umlauts; string concatenation would not.
            player = new MediaPlayer(new Media(track.file().toUri().toString()));
        } catch (RuntimeException e) { // MediaException for unsupported/broken files
            log.warn("Cannot load {}", track.file(), e);
            current = null;
            onChange.run();
            statusSink.error("Cannot play " + track.relative() + ": " + UiText.describe(e));
            return;
        }
        MediaPlayer p = player;
        p.setOnReady(() -> {
            if (p != player) return;
            statusSink.status("🎵 Playing: " + label());
            onChange.run();
        });
        // ID3 tags may trickle in after READY; refresh the tooltip when they do.
        p.getMedia().getMetadata().addListener((MapChangeListener<String, Object>) c -> {
            if (p == player) onChange.run();
        });
        p.setOnPlaying(onChange);
        p.setOnPaused(onChange);
        p.setOnEndOfMedia(() -> {
            if (p == player) playRandom();
        });
        // Errors don't throw — they arrive here, e.g. on Linux without a supported FFmpeg.
        // No auto-skip: if every file fails the same way, that would loop forever.
        p.setOnError(() -> {
            if (p != player) return;
            log.warn("Playback failed for {}", track.file(), p.getError());
            stop();
            statusSink.error("Cannot play " + track.relative() + ": " + UiText.describe(p.getError()));
        });
        p.play();
        onChange.run();
    }

    private void disposePlayer() {
        if (player == null) return;
        MediaPlayer p = player;
        player = null;
        p.stop();
        p.dispose();
    }
}
