package net.timafe.triptale;

import javafx.application.Preloader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Splash window shown while Spring boots in {@link TripTaleApplication#init()}. JavaFX starts the
 * preloader first and hides it on {@code BEFORE_START}, right before the main stage is created.
 * Registered through the {@code javafx.preloader} system property in {@link Launcher}.
 */
public class SplashPreloader extends Preloader {

    private static final int WIDTH = 420;
    /** Edge colour of the splash image background, so window and image blend seamlessly. */
    private static final String BACKGROUND = "#192B2B";

    private Stage stage;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        ImageView dragon = new ImageView(new Image(
                SplashPreloader.class.getResource("/images/triptale-splash.png").toExternalForm(),
                WIDTH * 2, 0, true, true));
        dragon.setFitWidth(WIDTH);
        dragon.setPreserveRatio(true);
        dragon.setSmooth(true);

        StackPane root = new StackPane(dragon);
        root.setStyle("-fx-background-color: " + BACKGROUND + ";");
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setScene(new Scene(root, Color.web(BACKGROUND)));
        stage.centerOnScreen();
        stage.show();
    }

    @Override
    public void handleStateChangeNotification(StateChangeNotification n) {
        if (n.getType() == StateChangeNotification.Type.BEFORE_START) {
            stage.hide();
        }
    }
}
