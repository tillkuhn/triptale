package net.timafe.triptale.ui.dialog;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import javafx.stage.Window;
import net.timafe.triptale.radio.Station;
import net.timafe.triptale.ui.RadioPlayer;

/**
 * Small, non-modal Tailwind FM window (a plain {@link Stage}) (todo 46): logo, now-playing and DJ line, and the
 * transport buttons. Stays open while the user keeps writing; it observes the
 * {@link RadioPlayer} and refreshes itself on every state change. Holds no state of its own.
 */
public final class TailwindFmDialog {

    /** Displayed width; the source image is 1000px wide so it stays sharp on Retina. */
    private static final double LOGO_WIDTH = 400;
    private static final double DJ_LINE_HEIGHT = 36;
    private static final double BUTTON_WIDTH = 84;

    private final RadioPlayer radio;
    private final Stage stage = new Stage();
    private final Label nowPlaying = new Label();
    private final Label djLine = new Label();
    private final Button playPause = new Button();
    private final Runnable refresh = this::refresh;

    public TailwindFmDialog(RadioPlayer radio, Window owner) {
        this.radio = radio;

        ImageView logo = new ImageView(new Image(
                TailwindFmDialog.class.getResource("/images/tailwind-fm.png").toExternalForm(),
                LOGO_WIDTH * 2, 0, true, true));
        logo.setFitWidth(LOGO_WIDTH);
        logo.setPreserveRatio(true);
        logo.setSmooth(true);

        nowPlaying.setWrapText(true);
        nowPlaying.setMaxWidth(LOGO_WIDTH);
        nowPlaying.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        djLine.setWrapText(true);
        djLine.setMaxWidth(LOGO_WIDTH);
        djLine.setStyle("-fx-font-style: italic; -fx-opacity: 0.8;");
        // Reserve room for two lines at all times, so the buttons don't jump when a DJ line
        // comes and goes.
        djLine.setMinHeight(DJ_LINE_HEIGHT);
        djLine.setPrefHeight(DJ_LINE_HEIGHT);
        djLine.setMaxHeight(DJ_LINE_HEIGHT);
        djLine.setAlignment(Pos.TOP_LEFT);

        // One fixed width for every label, so the row doesn't jump when Play becomes Pause.
        playPause.setOnAction(e -> radio.togglePause());
        Button next = new Button("🎲 Next");
        next.setOnAction(e -> radio.playRandom());
        Button stop = new Button("⏹ Stop");
        stop.setOnAction(e -> radio.stop());
        Button close = new Button("Close");
        close.setOnAction(e -> stage.hide());
        for (Button b : new Button[]{playPause, next, stop, close}) {
            b.setMinWidth(BUTTON_WIDTH);
            b.setPrefWidth(BUTTON_WIDTH);
        }
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(8, playPause, next, stop, spacer, close);
        buttons.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(8, logo, nowPlaying, djLine, buttons);
        box.setPadding(new Insets(12));

        // A plain Stage rather than a Dialog: no button bar, so Close can sit in our own row.
        Scene scene = new Scene(box);
        scene.getStylesheets().add(
                TailwindFmDialog.class.getResource("/fxml/triptale.css").toExternalForm());
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) stage.hide();
        });
        stage.initOwner(owner); // non-modal by default
        stage.setTitle(Station.NAME);
        stage.setScene(scene);
        stage.setResizable(false);
        stage.setOnShown(e -> radio.addChangeListener(refresh));
        stage.setOnHidden(e -> radio.removeChangeListener(refresh));
        refresh();
    }

    /** Shows the window, or brings it to the front if it is already open. */
    public void show() {
        refresh();
        if (stage.isShowing()) {
            stage.toFront();
            stage.requestFocus();
        } else {
            stage.show();
        }
    }

    private void refresh() {
        boolean loaded = radio.isLoaded();
        nowPlaying.setText(loaded ? "📻 " + radio.label() : "📻 Off air");
        String dj = radio.djLine();
        djLine.setText(dj.isEmpty() ? "" : "“" + dj + "”");
        playPause.setText(radio.isPlaying() ? "⏸ Pause" : "▶ Play");
    }
}
