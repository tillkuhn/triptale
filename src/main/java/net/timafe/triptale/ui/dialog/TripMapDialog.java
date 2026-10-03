package net.timafe.triptale.ui.dialog;

import javafx.geometry.Pos;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import net.timafe.triptale.ui.Dialogs;
import net.timafe.triptale.ui.MapboxService;

import java.util.List;

/**
 * Shows a Mapbox static map with one pin per recorded start point for a trip, plus a
 * differently-colored pin for its latest stop point (if recorded).
 * See {@link MapboxService} for the token setup.
 */
public final class TripMapDialog {

    private static final int WIDTH = 640;
    private static final int HEIGHT = 480;

    private final MapboxService mapboxService;

    public TripMapDialog(MapboxService mapboxService) {
        this.mapboxService = mapboxService;
    }

    /** {@code startPoints} and {@code endPoint} entries are {@code {lon, lat}}; {@code endPoint} may be null. */
    public void show(String tripName, List<double[]> startPoints, double[] endPoint) {
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Start Points · " + tripName);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        StackPane content = new StackPane();
        content.setPrefSize(WIDTH, HEIGHT);
        content.setMinSize(WIDTH, HEIGHT);

        if (!mapboxService.hasToken()) {
            content.getChildren().add(new Label("No Mapbox token configured — set one in Edit Settings."));
        } else if (startPoints.isEmpty() && endPoint == null) {
            content.getChildren().add(new Label("No start or stop points recorded for this trip yet."));
        } else {
            String url = mapboxService.staticImageUrl(startPoints, endPoint, WIDTH, HEIGHT);
            Image image = new Image(url, true);
            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(WIDTH);
            imageView.setFitHeight(HEIGHT);
            imageView.setPreserveRatio(true);

            Label statusLabel = new Label("Loading map…");
            statusLabel.setWrapText(true);
            statusLabel.setMaxWidth(WIDTH - 40);
            statusLabel.setAlignment(Pos.CENTER);

            image.errorProperty().addListener((obs, wasError, isError) -> {
                if (isError) {
                    Throwable ex = image.getException();
                    statusLabel.setText("Failed to load map"
                            + (ex != null && ex.getMessage() != null ? ": " + ex.getMessage() : "."));
                    statusLabel.setVisible(true);
                    statusLabel.setManaged(true);
                }
            });
            image.progressProperty().addListener((obs, oldV, newV) -> {
                if (newV.doubleValue() >= 1.0 && !image.isError()) {
                    statusLabel.setVisible(false);
                    statusLabel.setManaged(false);
                }
            });

            content.getChildren().addAll(imageView, statusLabel);
        }

        dlg.getDialogPane().setContent(content);
        Dialogs.applyStylesheet(dlg.getDialogPane());
        dlg.showAndWait();
    }
}
