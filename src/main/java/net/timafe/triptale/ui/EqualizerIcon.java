package net.timafe.triptale.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Tiny "equalizer" of bouncing bars shown on the radio button while a track plays (todo 46).
 * Fake, not driven by the audio. Heights jump in discrete steps a few times per second (no
 * interpolation), so the scene is only redrawn on those steps. {@link #stop()} it when hidden.
 */
public class EqualizerIcon extends HBox {

    private static final double MIN_HEIGHT = 3;

    private final Rectangle[] bars;
    private final double maxHeight;
    private final Timeline timeline;

    /** The tiny 4-bar version for the toolbar button. */
    EqualizerIcon() {
        this(4, 3, 2, 14);
    }

    /** A custom size, e.g. the wide Winamp-style strip in the radio window. */
    public EqualizerIcon(int barCount, double barWidth, double gap, double maxHeight) {
        super(gap);
        this.bars = new Rectangle[barCount];
        this.maxHeight = maxHeight;
        setAlignment(Pos.BOTTOM_CENTER);
        setMinSize(USE_PREF_SIZE, maxHeight);
        setPrefHeight(maxHeight);
        setMaxSize(USE_PREF_SIZE, maxHeight);
        for (int i = 0; i < barCount; i++) {
            bars[i] = new Rectangle(barWidth, MIN_HEIGHT);
            bars[i].getStyleClass().add("equalizer-bar");
            getChildren().add(bars[i]);
        }
        timeline = new Timeline(new KeyFrame(Duration.millis(130), e -> bounce()));
        timeline.setCycleCount(Timeline.INDEFINITE);
    }

    public void start() {
        timeline.play();
    }

    public void stop() {
        timeline.stop();
    }

    private void bounce() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (Rectangle bar : bars) {
            bar.setHeight(rnd.nextDouble(MIN_HEIGHT, maxHeight));
        }
    }
}
