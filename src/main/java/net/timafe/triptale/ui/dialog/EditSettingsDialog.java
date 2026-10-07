package net.timafe.triptale.ui.dialog;

import javafx.event.ActionEvent;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;
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
        AppSettings.Attachments attachments = settings.getAttachments();
        ComboBox<AppSettings.AttachmentSync> syncCombo = new ComboBox<>();
        syncCombo.getItems().setAll(AppSettings.AttachmentSync.values());
        syncCombo.setValue(attachments.getSync());
        syncCombo.setConverter(new StringConverter<>() {
            @Override public String toString(AppSettings.AttachmentSync s) { return s == null ? "" : syncLabel(s); }
            @Override public AppSettings.AttachmentSync fromString(String s) { return null; }
        });
        TextField bucketUrlField = new TextField(attachments.getBucketUrl());
        bucketUrlField.setPromptText("e.g. s3://triptale-attachments or s3://bucket/prefix");
        bucketUrlField.setPrefColumnCount(36);
        TextField regionField = new TextField(attachments.getRegion());
        regionField.setPromptText("e.g. " + AppSettings.Attachments.DEFAULT_REGION);
        regionField.setPrefColumnCount(16);
        TextField accessKeyIdField = new TextField(attachments.getAccessKeyId());
        accessKeyIdField.setPrefColumnCount(36);
        PasswordField secretField = new PasswordField();
        secretField.setText(attachments.getSecretAccessKey());
        secretField.setPrefColumnCount(36);
        CheckBox radioEnabledCheck = new CheckBox("Enable Radio (restart required)");
        radioEnabledCheck.setSelected(settings.isRadioEnabled());
        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error");

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
        grid.add(mapboxTokenField, 1, row++);
        grid.add(new Separator(), 0, row, 2, 1);
        row++;
        grid.add(new Label("Attachments sync:"), 0, row);
        grid.add(syncCombo, 1, row++);
        grid.add(new Label("Attachments bucket URL:"), 0, row);
        grid.add(bucketUrlField, 1, row++);
        grid.add(new Label("Attachments region:"), 0, row);
        grid.add(regionField, 1, row++);
        grid.add(new Label("Access key ID:"), 0, row);
        grid.add(accessKeyIdField, 1, row++);
        grid.add(new Label("Secret access key:"), 0, row);
        grid.add(secretField, 1, row++);
        grid.add(new Separator(), 0, row, 2, 1);
        row++;
        grid.add(radioEnabledCheck, 1, row++);
        grid.add(errorLabel, 1, row);

        dlg.getDialogPane().setContent(grid);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Dialogs.applyStylesheet(dlg.getDialogPane());

        // Keep the dialog open while the attachment fields are malformed
        dlg.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, ev -> {
            Optional<String> error = readAttachments(syncCombo, bucketUrlField, regionField, accessKeyIdField, secretField)
                    .validationError();
            errorLabel.setText(error.orElse(""));
            if (error.isPresent()) ev.consume();
        });

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
        settings.setAttachments(readAttachments(syncCombo, bucketUrlField, regionField, accessKeyIdField, secretField));
        settings.setRadioEnabled(radioEnabledCheck.isSelected());
        return Optional.of(settings);
    }

    private static String syncLabel(AppSettings.AttachmentSync sync) {
        return switch (sync) {
            case OFF -> "Off — local only, not in git";
            case GIT -> "Git — committed with the tales";
            case CLOUD -> "Cloud — pushed to the S3 bucket";
        };
    }

    private static AppSettings.Attachments readAttachments(ComboBox<AppSettings.AttachmentSync> syncCombo,
                                                           TextField bucketUrlField, TextField regionField,
                                                           TextField accessKeyIdField, PasswordField secretField) {
        AppSettings.Attachments attachments = new AppSettings.Attachments();
        attachments.setSync(syncCombo.getValue());
        attachments.setBucketUrl(bucketUrlField.getText().trim());
        attachments.setRegion(regionField.getText().trim());
        attachments.setAccessKeyId(accessKeyIdField.getText().trim());
        attachments.setSecretAccessKey(secretField.getText().trim());
        return attachments;
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
