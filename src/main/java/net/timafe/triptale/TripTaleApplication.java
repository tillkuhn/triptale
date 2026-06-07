package net.timafe.triptale;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import net.timafe.triptale.config.TripTaleProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@EnableConfigurationProperties(TripTaleProperties.class)
public class TripTaleApplication extends Application {

    private ConfigurableApplicationContext spring;

    public static void main(String[] args) {
        Application.launch(TripTaleApplication.class, args);
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
        stage.setTitle("TripTale");
        stage.setScene(new Scene(root, 900, 600));
        stage.show();
    }

    @Override
    public void stop() {
        if (spring != null) spring.close();
        Platform.exit();
    }
}
