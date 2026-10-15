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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.regex.Pattern;


@SpringBootApplication
@EnableConfigurationProperties(TripTaleProperties.class)
public class TripTaleApplication extends Application {

    private static final Logger log = LoggerFactory.getLogger(TripTaleApplication.class);

    private ConfigurableApplicationContext spring;
    private MainController controller;

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
        // Read by logging.pattern.console in application.yml to shorten the home dir to "~".
        System.setProperty("triptale.home-regex", Pattern.quote(System.getProperty("user.home")));
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
        controller = loader.getController();
        // Red close button goes through the same unsaved-changes prompt as Quit.
        stage.setOnCloseRequest(e -> {
            e.consume();
            controller.onExit();
        });
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
        log.info("Shutting down");
        if (controller != null) controller.saveSessionState();
        if (spring != null) spring.close();
        Platform.exit();
    }
}
