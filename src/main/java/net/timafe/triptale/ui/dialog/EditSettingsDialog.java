package net.timafe.triptale.ui.dialog;

import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Window;
import javafx.util.StringConverter;
import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.i18n.TripLanguage;
import net.timafe.triptale.radio.Station;
import net.timafe.triptale.ui.BrowserLauncher;
import net.timafe.triptale.ui.Dialogs;
import net.timafe.triptale.wordpress.WordPressAuthClient;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * Editor for the app-wide settings file. Returns the mutated {@link AppSettings} instance it was
 * given; the caller persists it via {@code SettingsStore} and decides whether the data-dir change
 * warrants a restart hint.
 */
public final class EditSettingsDialog {

    private static final int DEFAULT_GRID_COLUMNS = 2;

    private final BrowserLauncher browser;

    public EditSettingsDialog(BrowserLauncher browser) {
        this.browser = browser;
    }

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
        patternField.setPromptText("e.g. ${HOME}/Pictures/${TRIP_YEAR}_${TRIP_MONTH}_*/${ENTRY_YEAR}_${ENTRY_MONTH}_${ENTRY_DAY}/*.jpg");
        patternField.setPrefColumnCount(36);
        TextField columnsField = new TextField(Integer.toString(settings.getImpressionsGridColumns()));
        columnsField.setPrefColumnCount(4);
        TextField baseFilterField = new TextField(settings.getImpressionsBaseFilter());
        baseFilterField.setPromptText("e.g. " + AppSettings.DEFAULT_IMPRESSIONS_BASE_FILTER);
        baseFilterField.setPrefColumnCount(36);
        TextField faveFilterField = new TextField(settings.getImpressionsFaveFilter());
        faveFilterField.setPromptText("e.g. *+.* (space-separated, case-insensitive)");
        faveFilterField.setPrefColumnCount(36);
        TextField mapboxTokenField = new TextField(settings.getMapboxToken());
        mapboxTokenField.setPromptText("pk.… (unrestricted public token)");
        mapboxTokenField.setPrefColumnCount(36);
        ComboBox<TripLanguage> languageCombo = new ComboBox<>();
        languageCombo.getItems().setAll(TripLanguage.values());
        languageCombo.setValue(TripLanguage.fromCode(settings.getDefaultTripLanguage()));
        languageCombo.setConverter(new StringConverter<>() {
            @Override public String toString(TripLanguage l) { return l == null ? "" : l.displayName(); }
            @Override public TripLanguage fromString(String s) { return null; }
        });
        AppSettings.Attachments attachments = settings.getAttachments();
        ComboBox<AppSettings.AttachmentSync> syncCombo = new ComboBox<>();
        syncCombo.getItems().setAll(AppSettings.AttachmentSync.values());
        syncCombo.setValue(attachments.getSync());
        syncCombo.setConverter(new StringConverter<>() {
            @Override public String toString(AppSettings.AttachmentSync s) { return s == null ? "" : syncLabel(s); }
            @Override public AppSettings.AttachmentSync fromString(String s) { return null; }
        });
        TextField bucketUrlField = new TextField(attachments.getBucketUrl());
        bucketUrlField.setPromptText("e.g. s3://triptale-backpack or s3://bucket/prefix");
        bucketUrlField.setPrefColumnCount(36);
        TextField regionField = new TextField(attachments.getRegion());
        regionField.setPromptText("e.g. " + AppSettings.Attachments.DEFAULT_REGION);
        regionField.setPrefColumnCount(16);
        TextField accessKeyIdField = new TextField(attachments.getAccessKeyId());
        accessKeyIdField.setPrefColumnCount(36);
        PasswordField secretField = new PasswordField();
        secretField.setText(attachments.getSecretAccessKey());
        secretField.setPrefColumnCount(36);
        TextField publicBaseUrlField = new TextField(attachments.getPublicBaseUrl());
        publicBaseUrlField.setPromptText("optional, e.g. https://d123.cloudfront.net/ (WordPress export images)");
        publicBaseUrlField.setPrefColumnCount(36);
        CheckBox radioEnabledCheck = new CheckBox("Enable " + Station.NAME + " radio (restart required)");
        radioEnabledCheck.setSelected(settings.isRadioEnabled());
        AppSettings.WordPress wordPress = settings.getWordPress();
        TextField wpSiteField = new TextField(wordPress.getSite());
        wpSiteField.setPromptText("e.g. timafe.wordpress.com");
        wpSiteField.setPrefColumnCount(36);
        TextField wpClientIdField = new TextField(wordPress.getClientId());
        wpClientIdField.setPrefColumnCount(36);
        PasswordField wpClientSecretField = new PasswordField();
        wpClientSecretField.setText(wordPress.getClientSecret());
        wpClientSecretField.setPrefColumnCount(36);
        // Mutated only by the Connect action below, not by typing — the token comes from the
        // OAuth2 flow, never from a text field. A one-element array since it's captured by lambdas.
        String[] accessToken = { wordPress.getAccessToken() };
        Label wpTokenStatus = new Label(connectedLabel(accessToken[0]));
        wpTokenStatus.getStyleClass().add("hint-label");
        Button wpConnectButton = new Button("🔗 Connect to WordPress");
        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("form-error");
        errorLabel.managedProperty().bind(errorLabel.textProperty().isNotEmpty());

        GridPane general = Dialogs.formGrid();
        int row = 0;
        general.add(new Label("Data directory:"), 0, row);
        general.add(dataDirField, 1, row++);
        general.add(new Label("Git author name:"), 0, row);
        general.add(authorNameField, 1, row++);
        general.add(new Label("Git author email:"), 0, row);
        general.add(authorEmailField, 1, row++);
        general.add(new Separator(), 0, row, 2, 1);
        row++;
        general.add(new Label("Trip language:"), 0, row);
        general.add(languageCombo, 1, row++);
        Label languageHint = new Label("For words the exports add (Day, Distance, …); the app itself stays English");
        languageHint.getStyleClass().add("hint-label");
        general.add(languageHint, 1, row++);
        general.add(new Separator(), 0, row, 2, 1);
        row++;
        general.add(radioEnabledCheck, 1, row);

        GridPane photos = Dialogs.formGrid();
        row = 0;
        photos.add(new Label("Impressions file pattern:"), 0, row);
        photos.add(patternField, 1, row++);
        photos.add(new Label("Impressions grid columns:"), 0, row);
        photos.add(columnsField, 1, row++);
        photos.add(new Label("Impressions base filter:"), 0, row);
        photos.add(baseFilterField, 1, row++);
        photos.add(new Label("Impressions fave filter:"), 0, row);
        photos.add(faveFilterField, 1, row++);
        photos.add(new Separator(), 0, row, 2, 1);
        row++;
        photos.add(new Label("Mapbox token:"), 0, row);
        photos.add(mapboxTokenField, 1, row);

        GridPane backpack = Dialogs.formGrid();
        row = 0;
        backpack.add(new Label("Backpack sync:"), 0, row);
        backpack.add(syncCombo, 1, row++);
        backpack.add(new Label("Backpack bucket URL:"), 0, row);
        backpack.add(bucketUrlField, 1, row++);
        backpack.add(new Label("Backpack region:"), 0, row);
        backpack.add(regionField, 1, row++);
        backpack.add(new Label("Access key ID:"), 0, row);
        backpack.add(accessKeyIdField, 1, row++);
        backpack.add(new Label("Secret access key:"), 0, row);
        backpack.add(secretField, 1, row++);
        backpack.add(new Label("Backpack public URL:"), 0, row);
        backpack.add(publicBaseUrlField, 1, row);

        Tab backpackTab = tab("Backpack", backpack);

        GridPane wordpress = Dialogs.formGrid();
        row = 0;
        wordpress.add(new Label("Site:"), 0, row);
        wordpress.add(wpSiteField, 1, row++);
        wordpress.add(new Label("Client ID:"), 0, row);
        wordpress.add(wpClientIdField, 1, row++);
        wordpress.add(new Label("Client secret:"), 0, row);
        wordpress.add(wpClientSecretField, 1, row++);
        wordpress.add(new Label("Status:"), 0, row);
        wordpress.add(new HBox(8, wpConnectButton, wpTokenStatus), 1, row);
        Tab wordpressTab = tab("WordPress", wordpress);

        TabPane tabs = new TabPane(tab("General", general), tab("Photos & Map", photos), backpackTab, wordpressTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getStyleClass().add("settings-tabs");

        wpConnectButton.setOnAction(e -> {
            AppSettings.WordPress candidate = readWordPress(wpSiteField, wpClientIdField, wpClientSecretField, accessToken[0]);
            Optional<String> candidateError = candidate.validationError();
            if (candidateError.isPresent()) {
                errorLabel.setText(candidateError.get());
                tabs.getSelectionModel().select(wordpressTab);
                return;
            }
            errorLabel.setText("");
            wpConnectButton.setDisable(true);
            wpTokenStatus.setText("Opening browser for WordPress login…");
            Task<WordPressAuthClient.TokenResult> task = new Task<>() {
                @Override
                protected WordPressAuthClient.TokenResult call() {
                    return new WordPressAuthClient().connect(candidate, browser::open, Duration.ofMinutes(2));
                }
            };
            task.setOnSucceeded(ev -> {
                accessToken[0] = task.getValue().accessToken();
                wpTokenStatus.setText(connectedLabel(accessToken[0]));
                wpConnectButton.setDisable(false);
            });
            task.setOnFailed(ev -> {
                wpConnectButton.setDisable(false);
                wpTokenStatus.setText(connectedLabel(accessToken[0]));
                Throwable ex = task.getException();
                errorLabel.setText(ex == null ? "Connection failed" : String.valueOf(ex.getMessage()));
            });
            Thread thread = new Thread(task, "wordpress-oauth");
            thread.setDaemon(true);
            thread.start();
        });


        // Tabs keep the dialog short; the scroll pane is the fallback for screens that are
        // still too small, so OK/Cancel (outside the content) always stay reachable.
        ScrollPane scroll = new ScrollPane(new VBox(8, tabs, errorLabel));
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.getStyleClass().add("settings-scroll");

        dlg.getDialogPane().setContent(scroll);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.setResizable(true);
        dlg.setOnShown(e -> fitToScreen(dlg.getDialogPane().getScene().getWindow()));
        Dialogs.applyStylesheet(dlg.getDialogPane());

        // Keep the dialog open while the attachment fields are malformed
        dlg.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, ev -> {
            Optional<String> error = readAttachments(syncCombo, bucketUrlField, regionField, accessKeyIdField, secretField, publicBaseUrlField)
                    .validationError();
            if (error.isEmpty()) {
                error = readWordPress(wpSiteField, wpClientIdField, wpClientSecretField, accessToken[0])
                        .validationError();
                errorLabel.setText(error.orElse(""));
                if (error.isPresent()) {
                    tabs.getSelectionModel().select(wordpressTab);
                    ev.consume();
                }
                return;
            }
            errorLabel.setText(error.orElse(""));
            if (error.isPresent()) {
                tabs.getSelectionModel().select(backpackTab);
                ev.consume();
            }
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
        settings.setImpressionsBaseFilter(baseFilterField.getText().trim());
        settings.setImpressionsFaveFilter(faveFilterField.getText().trim());
        settings.setMapboxToken(mapboxTokenField.getText().trim());
        settings.setDefaultTripLanguage(languageCombo.getValue().code());
        settings.setAttachments(readAttachments(syncCombo, bucketUrlField, regionField, accessKeyIdField, secretField, publicBaseUrlField));
        settings.setRadioEnabled(radioEnabledCheck.isSelected());
        settings.setWordPress(readWordPress(wpSiteField, wpClientIdField, wpClientSecretField, accessToken[0]));
        return Optional.of(settings);
    }

    private static Tab tab(String title, GridPane content) {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    /** Shrinks the window to the screen's usable area (the content then scrolls) and keeps it on screen. */
    private static void fitToScreen(Window window) {
        Rectangle2D bounds = Screen.getScreensForRectangle(window.getX(), window.getY(), window.getWidth(), window.getHeight())
                .stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds();
        if (window.getHeight() > bounds.getHeight()) window.setHeight(bounds.getHeight());
        if (window.getWidth() > bounds.getWidth()) window.setWidth(bounds.getWidth());
        window.setY(Math.max(bounds.getMinY(), Math.min(window.getY(), bounds.getMaxY() - window.getHeight())));
        window.setX(Math.max(bounds.getMinX(), Math.min(window.getX(), bounds.getMaxX() - window.getWidth())));
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
                                                           TextField accessKeyIdField, PasswordField secretField,
                                                           TextField publicBaseUrlField) {
        AppSettings.Attachments attachments = new AppSettings.Attachments();
        attachments.setSync(syncCombo.getValue());
        attachments.setBucketUrl(bucketUrlField.getText().trim());
        attachments.setRegion(regionField.getText().trim());
        attachments.setAccessKeyId(accessKeyIdField.getText().trim());
        attachments.setSecretAccessKey(secretField.getText().trim());
        attachments.setPublicBaseUrl(publicBaseUrlField.getText().trim());
        return attachments;
    }

    private static AppSettings.WordPress readWordPress(TextField siteField, TextField clientIdField,
                                                        PasswordField clientSecretField, String existingAccessToken) {
        AppSettings.WordPress wordPress = new AppSettings.WordPress();
        wordPress.setSite(siteField.getText().trim());
        wordPress.setClientId(clientIdField.getText().trim());
        wordPress.setClientSecret(clientSecretField.getText().trim());
        // Not editable here — set only by the OAuth2 flow (todo 60); preserved across saves.
        wordPress.setAccessToken(existingAccessToken);
        return wordPress;
    }

    private static String connectedLabel(String accessToken) {
        return accessToken == null || accessToken.isBlank() ? "Not connected" : "Connected (access token stored)";
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
