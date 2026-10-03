package net.timafe.triptale.ui.dialog;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.ui.Dialogs;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Editor for the app-wide settings file. Returns the mutated {@link AppSettings} instance it was
 * given; the caller persists it via {@code SettingsStore} and decides whether the data-dir change
 * warrants a restart hint.
 */
public final class EditSettingsDialog {

    private static final int DEFAULT_GRID_COLUMNS = 2;

    public Optional<AppSettings> showAndWait(AppSettings settings, Path settingsFile) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Edit Settings");
        dlg.setHeaderText("App-wide settings — File: " + settingsFile);

        TextField dataDirField = new TextField(settings.getDataDir());
        dataDirField.setPromptText("e.g. ${HOME}/git/triptale-data");
        dataDirField.setPrefColumnCount(36);
        TextField authorNameField = new TextField(settings.getGit().getAuthorName());
        authorNameField.setPrefColumnCount(36);
        TextField authorEmailField = new TextField(settings.getGit().getAuthorEmail());
        authorEmailField.setPrefColumnCount(36);
        TextField patternField = new TextField(settings.getImpressionsFilePattern());
        patternField.setPromptText("e.g. ${HOME}/Pictures/${TRIP_YEAR}/${TRIP_MONTH}_??_${TRIP_SLUG}/00_Faves/output/${DATE}*.jpg");
        patternField.setPrefColumnCount(36);
        TextField columnsField = new TextField(Integer.toString(settings.getImpressionsGridColumns()));
        columnsField.setPrefColumnCount(4);
        TextField favePatternField = new TextField(settings.getImpressionsFaveFilePattern());
        favePatternField.setPromptText("e.g. ${HOME}/Pictures/${TRIP_YEAR}/${TRIP_MONTH}_??_${TRIP_SLUG}/00_Faves/${DATE}*.jpg");
        favePatternField.setPrefColumnCount(36);
        TextField mapboxTokenField = new TextField(settings.getMapboxToken());
        mapboxTokenField.setPromptText("pk.… (unrestricted public token)");
        mapboxTokenField.setPrefColumnCount(36);

        GridPane grid = Dialogs.formGrid();
        int row = 0;
        grid.add(new Label("Data directory:"), 0, row);
        grid.add(dataDirField, 1, row++);
        grid.add(new Label("Git author name:"), 0, row);
        grid.add(authorNameField, 1, row++);
        grid.add(new Label("Git author email:"), 0, row);
        grid.add(authorEmailField, 1, row++);
        grid.add(new Separator(), 0, row, 2, 1);
        row++;
        grid.add(new Label("Impressions file pattern:"), 0, row);
        grid.add(patternField, 1, row++);
        grid.add(new Label("Impressions grid columns:"), 0, row);
        grid.add(columnsField, 1, row++);
        grid.add(new Label("Faves file pattern:"), 0, row);
        grid.add(favePatternField, 1, row++);
        grid.add(new Separator(), 0, row, 2, 1);
        row++;
        grid.add(new Label("Mapbox token:"), 0, row);
        grid.add(mapboxTokenField, 1, row);

        dlg.getDialogPane().setContent(grid);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Dialogs.applyStylesheet(dlg.getDialogPane());

        Optional<ButtonType> result = dlg.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return Optional.empty();

        AppSettings.Git git = new AppSettings.Git();
        git.setAuthorName(authorNameField.getText().trim());
        git.setAuthorEmail(authorEmailField.getText().trim());
        settings.setDataDir(dataDirField.getText().trim());
        settings.setGit(git);
        settings.setImpressionsFilePattern(patternField.getText().trim());
        settings.setImpressionsGridColumns(parseColumns(columnsField.getText()));
        settings.setImpressionsFaveFilePattern(favePatternField.getText().trim());
        settings.setMapboxToken(mapboxTokenField.getText().trim());
        return Optional.of(settings);
    }

    /** Garbage or a sub-1 value falls back rather than rejecting the whole save. */
    private static int parseColumns(String text) {
        try {
            return Math.max(1, Integer.parseInt(text.trim()));
        } catch (NumberFormatException nfe) {
            return DEFAULT_GRID_COLUMNS;
        }
    }
}
