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
class EqualizerIcon extends HBox {

    private static final int BARS = 4;
    private static final double MAX_HEIGHT = 14;
    private static final double MIN_HEIGHT = 3;

    private final Rectangle[] bars = new Rectangle[BARS];
    private final Timeline timeline;

    EqualizerIcon() {
        super(2);
        setAlignment(Pos.BOTTOM_CENTER);
        setMinSize(USE_PREF_SIZE, MAX_HEIGHT);
        setPrefHeight(MAX_HEIGHT);
        setMaxSize(USE_PREF_SIZE, MAX_HEIGHT);
        for (int i = 0; i < BARS; i++) {
            bars[i] = new Rectangle(3, MIN_HEIGHT);
            bars[i].getStyleClass().add("equalizer-bar");
            getChildren().add(bars[i]);
        }
        timeline = new Timeline(new KeyFrame(Duration.millis(130), e -> bounce()));
        timeline.setCycleCount(Timeline.INDEFINITE);
    }

    void start() {
        timeline.play();
    }

    void stop() {
        timeline.stop();
    }

    private void bounce() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (Rectangle bar : bars) {
            bar.setHeight(rnd.nextDouble(MIN_HEIGHT, MAX_HEIGHT));
        }
    }
}
