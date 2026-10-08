package net.timafe.triptale;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;
import net.timafe.triptale.config.TripTaleProperties;
import net.timafe.triptale.ui.MainController;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;


@SpringBootApplication
@EnableConfigurationProperties(TripTaleProperties.class)
public class TripTaleApplication extends Application {

    private ConfigurableApplicationContext spring;

    /**
     * Set by {@code make aot-train}: the app quits on its own shortly after the window is shown,
     * so the JVM exits normally and writes the AOT cache without anyone clicking anything.
     */
    static final String AOT_TRAINING_PROPERTY = "triptale.aot-training";

    /** Kept for IDE run configs; the jar's real entry point is {@link Launcher}. */
    public static void main(String[] args) {
        Launcher.main(args);
    }

    @Override
    public void init() {
        javafx.application.HostServices hs = getHostServices();
        SpringApplication app = new SpringApplication(TripTaleApplication.class);
        app.addInitializers(ctx -> ctx.getBeanFactory().registerSingleton("hostServices", hs));
        spring = app.run(getParameters().getRaw().toArray(new String[0]));
    }

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        loader.setControllerFactory(spring::getBean);
        Parent root = loader.load();
        MainController controller = loader.getController();
        stage.titleProperty().bind(controller.windowTitleProperty());
        stage.setScene(new Scene(root, 1150, 650));
        stage.setMinWidth(1100);
        stage.setMinHeight(550);
        stage.show();
        stage.toFront();
        stage.requestFocus();
        if (Boolean.getBoolean(AOT_TRAINING_PROPERTY)) {
            PauseTransition quit = new PauseTransition(Duration.seconds(3));
            quit.setOnFinished(e -> Platform.exit());
            quit.play();
        }
    }

    @Override
    public void stop() {
        if (spring != null) spring.close();
        Platform.exit();
    }
}
