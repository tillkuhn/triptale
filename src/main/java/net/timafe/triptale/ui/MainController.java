package net.timafe.triptale.ui;

import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.event.ActionEvent;
import javafx.util.StringConverter;
import net.timafe.triptale.config.TripTaleProperties;
import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.export.DiaryExporter;
import net.timafe.triptale.export.ImpressionsMode;
import net.timafe.triptale.git.GitService;
import net.timafe.triptale.storage.ExifInfo;
import net.timafe.triptale.storage.ExifReader;
import net.timafe.triptale.storage.ImpressionsResolver;
import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.util.RelativeTime;
import net.timafe.triptale.util.SaveTarget;
import net.timafe.triptale.util.Slugs;
import net.timafe.triptale.util.TextStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.nio.file.Files;

@Component
public class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    @FXML private ComboBox<Trip> tripCombo;
    @FXML private DatePicker datePicker;
    @FXML private TextField distanceField;
    @FXML private TextField altField;
    @FXML private TextField routeField;
    @FXML private TextField trackUrlField;
    @FXML private Button openTrackUrlButton;
    @FXML private Button impressionsButton;
    @FXML private Button favesButton;
    @FXML private TextArea talesArea;
    @FXML private Label talesLabel;
    @FXML private Label statusLabel;
    @FXML private Label tourDayLabel;
    @FXML private Button copyButton;
    @FXML private Button saveButton;
    @FXML private Button commitButton;
    @FXML private Button prevDayButton;
    @FXML private Button firstDayButton;
    @FXML private Button todayButton;
    @FXML private Button connectivityButton;
    @FXML private MenuItem pushMenuItem;
    @FXML private MenuItem pullMenuItem;

    private static final String CREATE = "Create";
    private static final String UPDATE = "Update";
    private static final int COMMIT_MSG_INLINE_LIMIT = 5;

    // Connectivity button style classes
    private static final String CONN_CHECKING     = "connectivity-checking";
    private static final String CONN_CONNECTED    = "connectivity-connected";
    private static final String CONN_DISCONNECTED = "connectivity-disconnected";
    private static final String CONN_ICON_CHECKING     = "⟳";
    private static final String CONN_ICON_CONNECTED    = "📶";
    private static final String CONN_ICON_DISCONNECTED = "📵";

    private String baselineDistance = "";
    private String baselineAlt = "";
    private String baselineRoute = "";
    private String baselineTrackUrl = "";
    private String baselineTales = "";
    private boolean entryExists;
    private Instant talesUpdatedAt;

    /** Guard flag to prevent listener re-entrancy when reverting a navigation on Cancel. */
    private boolean navigating = false;

    /** Tri-state: null = checking/unknown, true = online, false = offline */
    private Boolean connected = null;

    private final Map<String, String> pending = new LinkedHashMap<>();

    private final MarkdownStore store;
    private final GitService gitService;
    private final TripTaleProperties props;
    private final DiaryExporter diaryExporter;
    private final ImpressionsResolver impressionsResolver;
    private final ExifReader exifReader;
    private final ConnectivityService connectivityService;
    private final BuildProperties buildProperties;
    private final HostServices hostServices;

    public MainController(MarkdownStore store, GitService gitService, TripTaleProperties props,
                          DiaryExporter diaryExporter, ImpressionsResolver impressionsResolver,
                          ExifReader exifReader,
                          ConnectivityService connectivityService,
                          ObjectProvider<BuildProperties> buildPropertiesProvider,
                          ObjectProvider<HostServices> hostServicesProvider) {
        this.store = store;
        this.gitService = gitService;
        this.props = props;
        this.diaryExporter = diaryExporter;
        this.impressionsResolver = impressionsResolver;
        this.exifReader = exifReader;
        this.connectivityService = connectivityService;
        this.buildProperties = buildPropertiesProvider.getIfAvailable();
        this.hostServices = hostServicesProvider.getIfAvailable();
    }

    private static final DateTimeFormatter DATE_DISPLAY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd EEEE", Locale.ENGLISH);

    @FXML
    public void initialize() {
        datePicker.setConverter(new StringConverter<>() {
            @Override public String toString(LocalDate d) { return d == null ? "" : DATE_DISPLAY.format(d); }
            @Override public LocalDate fromString(String s) {
                if (s == null || s.isBlank()) return null;
                try { return LocalDate.parse(s.trim(), DATE_DISPLAY); } catch (Exception e) { return null; }
            }
        });
        tripCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Trip t) { return t == null ? "" : t.name() + " (" + t.slug() + ")"; }
            @Override public Trip fromString(String s) { return null; }
        });
        datePicker.setDayCellFactory(dp -> new DateCell() {
            @Override
            public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                Trip trip = tripCombo.getValue();
                if (!empty && item != null && trip != null && trip.startDate() != null
                        && item.isBefore(trip.startDate())) {
                    setDisable(true);
                    setStyle("-fx-background-color: #f0f0f0;");
                }
            }
        });
        reloadTrips();
        tripCombo.valueProperty().addListener((obs, old, sel) -> {
            if (navigating) return;
            if (sel == null) return;
            SaveTarget saveTarget = old != null
                    ? SaveTarget.forTripChange(old.slug(), datePicker.getValue())
                    : null;
            if (!confirmNavigateAway(saveTarget, () -> tripCombo.setValue(old))) return;
            store.saveLastTripSlug(sel.slug());
            List<LocalDate> dates = store.listEntryDates(sel.slug());
            LocalDate target;
            String reason;
            if (!dates.isEmpty()) {
                target = dates.get(dates.size() - 1);
                reason = "Opened last entry (" + target + ")";
            } else if (sel.startDate() != null) {
                target = sel.startDate();
                reason = "No entries — starting at day 1 (" + target + ")";
            } else {
                target = LocalDate.now();
                reason = "No entries — defaulting to today (" + target + ")";
            }
            datePicker.setValue(target);
            loadEntry();
            updatePrevButtonState();
            status(reason);
        });
        datePicker.valueProperty().addListener((obs, old, sel) -> {
            if (navigating) return;
            Trip trip = tripCombo.getValue();
            if (sel != null && trip != null && trip.startDate() != null
                    && sel.isBefore(trip.startDate())) {
                datePicker.setValue(trip.startDate());
                status("Snapped to day 1 (" + trip.startDate() + ")");
                return;
            }
            SaveTarget saveTarget = trip != null && old != null
                    ? SaveTarget.forDateChange(trip.slug(), old)
                    : null;
            if (!confirmNavigateAway(saveTarget, () -> datePicker.setValue(old))) return;
            loadEntry();
            updatePrevButtonState();
        });
        distanceField.textProperty().addListener((o, a, b) -> updateDirty());
        altField.textProperty().addListener((o, a, b) -> updateDirty());
        routeField.textProperty().addListener((o, a, b) -> updateDirty());
        trackUrlField.textProperty().addListener((o, a, b) -> updateDirty());
        talesArea.textProperty().addListener((o, a, b) -> updateDirty());
        if (!tripCombo.getItems().isEmpty()) {
            String lastSlug = store.loadLastTripSlug().orElse(null);
            Trip toSelect = lastSlug != null
                    ? tripCombo.getItems().stream().filter(t -> t.slug().equals(lastSlug)).findFirst().orElse(null)
                    : null;
            tripCombo.getSelectionModel().select(toSelect != null ? toSelect : tripCombo.getItems().get(0));
        }
        updateDirty();
        updatePrevButtonState();
        saveButton.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN),
                        () -> { if (isDirty()) onSave(); });
                newScene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN),
                        () -> { if (canCommit()) onCommit(); });
                newScene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.LEFT, KeyCombination.ALT_DOWN),
                        this::onPrevDay);
                newScene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.ALT_DOWN),
                        this::onNextDay);
            }
        });
        updateCommitButton();
        status("Data dir: " + props.resolvedDataDir());
        // Kick off a non-blocking connectivity check on startup
        triggerConnectivityCheck();
    }

    private void reloadTrips() {
        tripCombo.setItems(FXCollections.observableArrayList(store.listTrips()));
    }

    @FXML
    public void onNewTrip() {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("New Trip");
        dlg.setHeaderText("Create a new trip");

        TextField nameField = new TextField();
        nameField.setPromptText("Trip name");
        nameField.setPrefColumnCount(28);
        DatePicker startField = new DatePicker(LocalDate.now());
        TextArea descArea = new TextArea();
        descArea.setPromptText("Optional description");
        descArea.setPrefRowCount(3);
        descArea.setPrefColumnCount(28);
        descArea.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(14));
        grid.getStyleClass().add("card");
        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Start date:"), 0, 1);
        grid.add(startField, 1, 1);
        grid.add(new Label("Description:"), 0, 2);
        grid.add(descArea, 1, 2);

        dlg.getDialogPane().setContent(grid);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        applyStylesheet(dlg.getDialogPane());

        Node okButton = dlg.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setDisable(true);
        Runnable refreshOk = () -> okButton.setDisable(
                nameField.getText().isBlank() || startField.getValue() == null);
        nameField.textProperty().addListener((o, a, b) -> refreshOk.run());
        startField.valueProperty().addListener((o, a, b) -> refreshOk.run());

        Optional<ButtonType> result = dlg.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;
        String name = nameField.getText().trim();
        LocalDate start = startField.getValue();
        String desc = descArea.getText();
        String slug = Slugs.toSlug(name);
        Trip trip = new Trip(slug, name, start, desc);
        store.saveTrip(trip);
        addPending(slug, CREATE);
        reloadTrips();
        tripCombo.getSelectionModel().select(
                tripCombo.getItems().stream().filter(t -> t.slug().equals(slug)).findFirst().orElse(null));
        status("Created trip " + slug);
    }

    @FXML
    public void onTripDetails() {
        Trip trip = tripCombo.getValue();
        if (trip == null) { error("No trip selected"); return; }

        List<LocalDate> dates = store.listEntryDates(trip.slug());
        double totalDistance = 0;
        double totalAltitude = 0;
        int activeDays = 0;
        int activeAltitudeDays = 0;
        for (LocalDate d : dates) {
            DiaryEntry e = store.loadEntry(trip.slug(), d);
            if (e.distance() != null && e.distance() > 0) {
                totalDistance += e.distance();
                activeDays++;
            }
            if (e.altitudeMeters() != null && e.altitudeMeters() > 0) {
                totalAltitude += e.altitudeMeters();
                activeAltitudeDays++;
            }
        }

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(14));
        grid.getStyleClass().add("card");
        int row = 0;
        grid.add(new Label("Slug:"), 0, row);
        grid.add(new Label(trip.slug()), 1, row++);
        grid.add(new Label("Start date:"), 0, row);
        grid.add(new Label(trip.startDate() == null ? "—" : trip.startDate().toString()), 1, row++);
        grid.add(new Label("Entries:"), 0, row);
        grid.add(new Label(Integer.toString(dates.size())), 1, row++);
        grid.add(new Label("Total distance:"), 0, row);
        String distanceText = activeDays > 0
                ? String.format(Locale.ROOT, "%.1f km (avg %.1f km / active day, %d days)",
                        totalDistance, totalDistance / activeDays, activeDays)
                : String.format(Locale.ROOT, "%.1f km", totalDistance);
        grid.add(new Label(distanceText), 1, row++);
        grid.add(new Label("Total altitude:"), 0, row);
        String altitudeText = activeAltitudeDays > 0
                ? String.format(Locale.ROOT, "%.0f m (avg %.0f m / active day, %d days)",
                        totalAltitude, totalAltitude / activeAltitudeDays, activeAltitudeDays)
                : String.format(Locale.ROOT, "%.0f m", totalAltitude);
        grid.add(new Label(altitudeText), 1, row++);
        grid.add(new Label("Description:"), 0, row);
        TextArea descArea = new TextArea(trip.description() == null ? "" : trip.description());
        descArea.setEditable(false);
        descArea.setWrapText(true);
        descArea.setPrefRowCount(4);
        descArea.setPrefColumnCount(40);
        grid.add(descArea, 1, row);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Trip Details");
        alert.setHeaderText(trip.name());
        alert.getDialogPane().setContent(grid);
        alert.getButtonTypes().setAll(ButtonType.CLOSE);
        applyStylesheet(alert.getDialogPane());
        alert.showAndWait();
    }

    @FXML
    public void onExportDiary() {
        Trip trip = tripCombo.getValue();
        if (trip == null) { error("No trip selected"); return; }
        String exported;
        try {
            exported = diaryExporter.exportTrip(trip);
        } catch (RuntimeException e) {
            error("Export failed: " + e.getMessage());
            return;
        }

        TextArea ta = new TextArea(exported);
        ta.setEditable(false);
        ta.setWrapText(false);
        ta.setStyle("-fx-font-family: 'monospace';");
        ta.setPrefRowCount(28);
        ta.setPrefColumnCount(90);

        boolean impressionsConfigured = store.getImpressionsFilePattern().isPresent();
        boolean favesConfigured = store.getImpressionsFaveFilePattern().isPresent();
        ImpressionsMode defaultMode = impressionsConfigured
                ? ImpressionsMode.ALL
                : (favesConfigured ? ImpressionsMode.FAVES : ImpressionsMode.NONE);

        ComboBox<ImpressionsMode> impressionsCombo = new ComboBox<>(
                FXCollections.observableArrayList(ImpressionsMode.NONE, ImpressionsMode.FAVES, ImpressionsMode.ALL));
        impressionsCombo.setConverter(new StringConverter<>() {
            @Override public String toString(ImpressionsMode m) {
                if (m == null) return "";
                return switch (m) {
                    case NONE -> "None";
                    case FAVES -> "Fave Impressions";
                    case ALL -> "All Impressions";
                };
            }
            @Override public ImpressionsMode fromString(String s) { return null; }
        });
        impressionsCombo.setValue(defaultMode);

        HBox impressionsBox = new HBox(8, new Label("Images:"), impressionsCombo);
        impressionsBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        VBox contentBox = new VBox(8, ta, impressionsBox);

        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Export Diary");
        dlg.setHeaderText(trip.name());
        dlg.setResizable(true);
        dlg.getDialogPane().setContent(contentBox);
        applyStylesheet(dlg.getDialogPane());

        ButtonType copyType = new ButtonType("Copy", ButtonBar.ButtonData.OTHER);
        ButtonType previewType = new ButtonType("Preview in Browser", ButtonBar.ButtonData.OTHER);
        dlg.getDialogPane().getButtonTypes().setAll(copyType, previewType, ButtonType.CLOSE);

        Button copyBtn = (Button) dlg.getDialogPane().lookupButton(copyType);
        copyBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(exported);
            Clipboard.getSystemClipboard().setContent(cc);
            status("Diary copied to clipboard");
            ev.consume();
        });

        Button previewBtn = (Button) dlg.getDialogPane().lookupButton(previewType);
        previewBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            try {
                String html = diaryExporter.exportTripAsHtml(trip, impressionsCombo.getValue());
                java.nio.file.Path tmp = Files.createTempFile("triptale-export-", ".html");
                Files.writeString(tmp, html, java.nio.charset.StandardCharsets.UTF_8);
                tmp.toFile().deleteOnExit();
                openInBrowser(tmp.toUri().toString());
            } catch (IOException ex) {
                error("Preview failed: " + ex.getMessage());
            }
            ev.consume();
        });

        dlg.showAndWait();
    }

    @FXML
    public void onFirstDay() {
        Trip trip = tripCombo.getValue();
        if (trip == null || trip.startDate() == null) return;
        datePicker.setValue(trip.startDate());
        status("Snapped to day 1 (" + trip.startDate() + ")");
    }

    @FXML
    public void onPrevDay() {
        if (datePicker.getValue() != null) datePicker.setValue(datePicker.getValue().minusDays(1));
    }

    @FXML
    public void onNextDay() {
        if (datePicker.getValue() != null) datePicker.setValue(datePicker.getValue().plusDays(1));
    }

    @FXML
    public void onToday() {
        LocalDate today = LocalDate.now();
        datePicker.setValue(today);
        if (today.equals(datePicker.getValue())) {
            status("Snapped to today (" + today + ")");
        }
    }

    private void loadEntry() {
        Trip trip = tripCombo.getValue();
        LocalDate date = datePicker.getValue();
        updateTourDay(trip, date);
        if (trip == null || date == null) return;
        entryExists = store.entryExists(trip.slug(), date);
        DiaryEntry e = store.loadEntry(trip.slug(), date);
        distanceField.setText(e.distance() == null ? "" : e.distance().toString());
        altField.setText(e.altitudeMeters() == null ? "" : e.altitudeMeters().toString());
        routeField.setText(e.route() == null ? DiaryEntry.DEFAULT_ROUTE : e.route());
        trackUrlField.setText(e.trackUrl() == null ? "" : e.trackUrl());
        talesArea.setText(e.tales() == null ? "" : e.tales());
        talesUpdatedAt = readTalesLastModified(trip, date);
        updateTalesLabel();
        updateImpressionsButton(date);
        updateFavesButton(date);
        snapshotBaseline();
        updateDirty();
    }

    private void updateImpressionsButton(LocalDate date) {
        if (impressionsButton == null) return;
        String pattern = store.getImpressionsFilePattern().orElse(null);
        if (pattern == null || date == null) {
            impressionsButton.setText("No Impressions");
            impressionsButton.setDisable(true);
            return;
        }
        List<Path> images = impressionsResolver.resolve(pattern, date);
        if (images.isEmpty()) {
            impressionsButton.setText("No Impressions");
            impressionsButton.setDisable(true);
        } else {
            impressionsButton.setText(images.size() + " Impression" + (images.size() == 1 ? "" : "s") + " ›");
            impressionsButton.setDisable(false);
        }
    }

    private void updateFavesButton(LocalDate date) {
        if (favesButton == null) return;
        String pattern = store.getImpressionsFaveFilePattern().orElse(null);
        if (pattern == null || date == null) {
            favesButton.setText("No Faves");
            favesButton.setDisable(true);
            return;
        }
        List<Path> images = impressionsResolver.resolve(pattern, date);
        if (images.isEmpty()) {
            favesButton.setText("No Faves");
            favesButton.setDisable(true);
        } else {
            favesButton.setText(images.size() + " Fave" + (images.size() == 1 ? "" : "s") + " ›");
            favesButton.setDisable(false);
        }
    }

    private Instant readTalesLastModified(Trip trip, LocalDate date) {
        try {
            return Files.getLastModifiedTime(store.entryFile(trip.slug(), date)).toInstant();
        } catch (IOException ex) {
            return null;
        }
    }

    private void updateTalesLabel() {
        if (talesLabel == null) return;
        String text = talesArea.getText();
        if (text == null || text.isBlank()) {
            talesLabel.setText("🐉 Tales · here be dragons");
            return;
        }
        int words = TextStats.wordCount(text);
        String ago = talesUpdatedAt == null ? "just now" : RelativeTime.ago(talesUpdatedAt, Instant.now());
        talesLabel.setText("🐉 Tales · " + words + " word" + (words == 1 ? "" : "s") + " updated " + ago);
    }

    private void snapshotBaseline() {
        baselineDistance = distanceField.getText();
        baselineAlt = altField.getText();
        baselineRoute = routeField.getText();
        baselineTrackUrl = trackUrlField.getText();
        baselineTales = talesArea.getText();
    }

    private boolean isDirty() {
        return !Objects.equals(distanceField.getText(), baselineDistance)
                || !Objects.equals(altField.getText(), baselineAlt)
                || !Objects.equals(routeField.getText(), baselineRoute)
                || !Objects.equals(trackUrlField.getText(), baselineTrackUrl)
                || !Objects.equals(talesArea.getText(), baselineTales);
    }

    /**
     * If the form is dirty, shows a Save / Discard / Cancel dialog.
     * <ul>
     *   <li>Save — persists to {@code target} (the trip/date the unsaved edits actually belong to,
     *       <em>not</em> whatever the combo/picker controls currently report — see {@link SaveTarget})
     *       then returns {@code true} (navigation proceeds).</li>
     *   <li>Discard — returns {@code true} (navigation proceeds, changes are lost).</li>
     *   <li>Cancel — invokes {@code revert} to undo the navigation, returns {@code false}.</li>
     * </ul>
     * Returns {@code true} immediately when the form is not dirty.
     */
    private boolean confirmNavigateAway(SaveTarget target, Runnable revert) {
        if (!isDirty()) return true;
        String dateLabel = target != null && target.date() != null ? target.date().toString() : "current entry";
        ButtonType save    = new ButtonType("Save");
        ButtonType discard = new ButtonType("Discard");
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "You have unsaved changes for " + dateLabel + ". What would you like to do?",
                save, discard, ButtonType.CANCEL);
        alert.setTitle("Unsaved Changes");
        alert.setHeaderText("Unsaved changes");
        applyStylesheet(alert.getDialogPane());
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() == ButtonType.CANCEL) {
            navigating = true;
            try { revert.run(); } finally { navigating = false; }
            return false;
        }
        if (result.get() == save && target != null) {
            doSave(target.tripSlug(), target.date());
        }
        return true;
    }

    private void updateDirty() {
        boolean dirty = isDirty();
        if (saveButton != null) {
            saveButton.setDisable(!dirty);
            saveButton.setText(entryExists ? "💾 Save Tale" : "📝 Create Tale");
        }
        if (copyButton != null) {
            boolean hasContent = talesArea != null && !talesArea.getText().isBlank()
                    && tripCombo.getValue() != null && datePicker.getValue() != null;
            copyButton.setDisable(!hasContent);
        }
        if (openTrackUrlButton != null) {
            openTrackUrlButton.setDisable(!isValidHttpUrl(trackUrlField.getText()));
        }
        updateCommitButton();
    }

    private void addPending(String label, String action) {
        pending.putIfAbsent(label, action);
        updateCommitButton();
    }

    private boolean canCommit() {
        return !pending.isEmpty() && !isDirty();
    }

    private void updateCommitButton() {
        if (commitButton == null) return;
        commitButton.setText("📦 Commit (" + pending.size() + ")");
        commitButton.setDisable(!canCommit());
    }

    private String buildCommitMessage() {
        int total = pending.size();
        if (total <= COMMIT_MSG_INLINE_LIMIT) {
            List<String> creates = new ArrayList<>();
            List<String> updates = new ArrayList<>();
            pending.forEach((label, action) ->
                    (CREATE.equals(action) ? creates : updates).add(label));
            StringBuilder sb = new StringBuilder();
            if (!creates.isEmpty()) sb.append(CREATE).append(": ").append(String.join(", ", creates));
            if (!updates.isEmpty()) {
                if (sb.length() > 0) sb.append(" | ");
                sb.append(UPDATE).append(": ").append(String.join(", ", updates));
            }
            return sb.toString();
        }
        List<String> labels = new ArrayList<>(pending.keySet());
        String head = String.join(", ", labels.subList(0, COMMIT_MSG_INLINE_LIMIT));
        int more = total - COMMIT_MSG_INLINE_LIMIT;
        return UPDATE + ": " + head + ", ... and " + more + " more (" + total + " total)";
    }

    private void updatePrevButtonState() {
        Trip trip = tripCombo.getValue();
        LocalDate date = datePicker.getValue();
        boolean prevDisabled;
        if (trip == null || trip.startDate() == null || date == null) {
            prevDisabled = false;
        } else {
            prevDisabled = !date.isAfter(trip.startDate());
        }
        if (prevDayButton != null) prevDayButton.setDisable(prevDisabled);
        if (firstDayButton != null) firstDayButton.setDisable(prevDisabled);
        if (todayButton != null) {
            LocalDate target = LocalDate.now();
            if (trip != null && trip.startDate() != null && target.isBefore(trip.startDate())) {
                target = trip.startDate();
            }
            todayButton.setDisable(date == null || date.equals(target));
        }
    }

    private void updateTourDay(Trip trip, LocalDate date) {
        if (tourDayLabel == null) return;
        if (trip == null || trip.startDate() == null || date == null) {
            tourDayLabel.setText("");
            return;
        }
        long day = ChronoUnit.DAYS.between(trip.startDate(), date) + 1;
        tourDayLabel.setText("Day " + day + " (" + relativeDayLabel(date, day) + ")");
    }

    private static String relativeDayLabel(LocalDate date, long tourDay) {
        long delta = ChronoUnit.DAYS.between(LocalDate.now(), date);
        if (delta == 0) return "today";
        if (delta == -1) return "yesterday";
        if (delta == 1) return "tomorrow";
        if (delta < -1) return tourDay == 1 ? "first day" : (-delta) + " days ago";
        return "in " + delta + " days";
    }

    @FXML
    public void onSave() {
        Trip trip = tripCombo.getValue();
        LocalDate date = datePicker.getValue();
        if (trip == null) { error("No trip selected"); return; }
        if (date == null) { error("No date selected"); return; }
        doSave(trip.slug(), date);
    }

    /**
     * Persists the current form field contents to {@code tripSlug}/{@code date}.
     * <p>
     * Callers must pass the trip/date the form fields actually belong to explicitly rather than
     * re-reading {@code tripCombo.getValue()}/{@code datePicker.getValue()} — those controls may
     * already have advanced to a new selection (see {@link SaveTarget} and {@link #confirmNavigateAway}).
     */
    private void doSave(String tripSlug, LocalDate date) {
        Double distance = parseDouble(distanceField.getText(), "distance");
        Double alt = parseDouble(altField.getText(), "altitude");
        if (distance == null && !distanceField.getText().isBlank()) return;
        if (alt == null && !altField.getText().isBlank()) return;
        DiaryEntry entry = DiaryEntry.builder(date)
                .distance(distance)
                .altitudeMeters(alt)
                .route(routeField.getText())
                .trackUrl(trackUrlField.getText())
                .tales(talesArea.getText())
                .build();
        boolean wasNew = !entryExists;
        store.saveEntry(tripSlug, entry);
        entryExists = true;
        talesUpdatedAt = Instant.now();
        updateTalesLabel();
        addPending(tripSlug + "/" + date, wasNew ? CREATE : UPDATE);
        snapshotBaseline();
        updateDirty();
        status("Saved " + tripSlug + "/" + date);
    }

    @FXML
    public void onCopyTale() {
        String route = routeField.getText();
        String tales = talesArea.getText();
        StringBuilder sb = new StringBuilder();
        if (route != null && !route.isBlank() && !route.equals(DiaryEntry.DEFAULT_ROUTE)) {
            sb.append(route).append("\n\n");
        }
        if (tales != null) sb.append(tales);
        ClipboardContent cc = new ClipboardContent();
        cc.putString(sb.toString());
        Clipboard.getSystemClipboard().setContent(cc);
    }

    @FXML
    public void onCommit() {
        if (!canCommit()) return;
        String message = buildCommitMessage();
        String sha;
        try {
            sha = gitService.commitAll(message);
        } catch (RuntimeException e) {
            error(e.getMessage());
            return;
        }
        int n = pending.size();
        pending.clear();
        updateCommitButton();
        String suffix = sha != null ? " (" + sha + ")" : "";
        status("Committed " + n + " change" + (n == 1 ? "" : "s") + suffix);
    }

    @FXML
    public void onCheckConnectivity() {
        triggerConnectivityCheck();
    }

    private void triggerConnectivityCheck() {
        // Show "checking" state immediately on the FX thread
        connected = null;
        applyConnectivityState();

        String remoteUrl;
        try {
            remoteUrl = gitService.remoteUrl();
        } catch (RuntimeException e) {
            remoteUrl = "";
        }

        Task<Boolean> task = connectivityService.checkTask(remoteUrl);
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            connected = task.getValue();
            applyConnectivityState();
        }));
        task.setOnFailed(e -> Platform.runLater(() -> {
            connected = false;
            applyConnectivityState();
        }));
        Thread thread = new Thread(task, "connectivity-check");
        thread.setDaemon(true);
        thread.start();
    }

    private void applyConnectivityState() {
        boolean hasRemote = hasRemoteConfigured();
        if (connectivityButton != null) {
            connectivityButton.getStyleClass().removeAll(CONN_CHECKING, CONN_CONNECTED, CONN_DISCONNECTED);
            if (connected == null) {
                connectivityButton.getStyleClass().add(CONN_CHECKING);
                connectivityButton.setText(CONN_ICON_CHECKING);
                connectivityButton.setTooltip(new javafx.scene.control.Tooltip("Checking connectivity…"));
            } else if (connected) {
                connectivityButton.getStyleClass().add(CONN_CONNECTED);
                connectivityButton.setText(CONN_ICON_CONNECTED);
                connectivityButton.setTooltip(new javafx.scene.control.Tooltip("Connected — click to recheck"));
            } else {
                connectivityButton.getStyleClass().add(CONN_DISCONNECTED);
                connectivityButton.setText(CONN_ICON_DISCONNECTED);
                connectivityButton.setTooltip(new javafx.scene.control.Tooltip("Offline — click to recheck"));
            }
        }
        // Gray out push/pull when offline or no remote configured
        boolean remoteEnabled = Boolean.TRUE.equals(connected) && hasRemote;
        if (pushMenuItem != null) pushMenuItem.setDisable(!remoteEnabled);
        if (pullMenuItem != null) pullMenuItem.setDisable(!remoteEnabled);
    }

    private boolean hasRemoteConfigured() {
        try {
            return !gitService.remoteUrl().isBlank();
        } catch (RuntimeException e) {
            return false;
        }
    }

    @FXML
    public void onPull() {
        if (!pending.isEmpty()) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "You have " + pending.size() + " uncommitted save"
                            + (pending.size() == 1 ? "" : "s")
                            + ". Pull may fail or create a merge. Continue?",
                    ButtonType.OK, ButtonType.CANCEL);
            confirm.setTitle("Pull");
            confirm.setHeaderText("Uncommitted changes");
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) return;
        }
        try {
            gitService.pull();
            reloadTrips();
            loadEntry();
            status("Pulled from remote");
        } catch (RuntimeException e) {
            error(describe(e));
        }
    }

    @FXML
    public void onPush() {
        try {
            gitService.push();
            status("Pushed to remote");
        } catch (RuntimeException e) {
            error(describe(e));
        }
    }

    @FXML
    public void onRemoteInfo() {
        String actualRemote;
        try {
            actualRemote = gitService.remoteUrl();
        } catch (RuntimeException e) {
            actualRemote = "(error: " + e.getMessage() + ")";
        }
        String authorName = props.getGit().getAuthorName();
        String authorEmail = props.getGit().getAuthorEmail();
        String authorDisplay = (authorName.isBlank() && authorEmail.isBlank())
                ? "(system git config)"
                : (authorName + " <" + authorEmail + ">");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        grid.setPadding(new Insets(10));
        int row = 0;
        grid.add(new Label("Data dir:"), 0, row);
        grid.add(new Label(props.resolvedDataDir().toString()), 1, row++);
        grid.add(new Label("Origin URL:"), 0, row);
        grid.add(new Label(actualRemote.isBlank() ? "(none)" : actualRemote), 1, row++);
        grid.add(new Label("Author:"), 0, row);
        grid.add(new Label(authorDisplay), 1, row);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Remote Info");
        alert.setHeaderText("Git configuration");
        alert.getDialogPane().setContent(grid);
        alert.getButtonTypes().setAll(ButtonType.CLOSE);
        applyStylesheet(alert.getDialogPane());
        alert.showAndWait();
    }

    @FXML
    public void onExit() {
        boolean dirty = isDirty();
        boolean hasPending = !pending.isEmpty();
        if (!dirty && !hasPending) {
            Platform.exit();
            return;
        }
        StringBuilder body = new StringBuilder();
        if (dirty) body.append("You have unsaved edits to the current entry");
        if (dirty && hasPending) body.append("\nand ");
        if (hasPending) {
            body.append(pending.size())
                    .append(" saved change")
                    .append(pending.size() == 1 ? "" : "s")
                    .append(" not yet committed");
        }
        body.append(".");

        ButtonType commitAndExit = new ButtonType("Commit & Exit");
        ButtonType exitAnyway = new ButtonType("Exit anyway");
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, body.toString(),
                commitAndExit, exitAnyway, ButtonType.CANCEL);
        confirm.setTitle("Exit TripTale");
        applyStylesheet(confirm.getDialogPane());
        confirm.setHeaderText(dirty && hasPending
                ? "Unsaved and uncommitted changes"
                : (dirty ? "Unsaved changes" : "Uncommitted changes"));
        // Same rule as the main Commit button: cannot commit while form is dirty.
        Node commitBtn = confirm.getDialogPane().lookupButton(commitAndExit);
        if (commitBtn != null) commitBtn.setDisable(dirty || !hasPending);

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() == ButtonType.CANCEL) return;
        if (result.get() == commitAndExit) {
            try {
                gitService.commitAll(buildCommitMessage());
            } catch (RuntimeException e) {
                error(e.getMessage());
                return;
            }
        }
        Platform.exit();
    }

    @FXML
    public void onAbout() {
        String version = buildProperties != null ? buildProperties.getVersion() : "dev";
        String builtAt = (buildProperties != null && buildProperties.getTime() != null)
                ? DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        .withZone(ZoneId.systemDefault())
                        .format(buildProperties.getTime())
                : "—";
        String javaVersion = System.getProperty("java.version", "?");
        String javafxVersion = System.getProperty("javafx.runtime.version",
                System.getProperty("javafx.version", "?"));
        String repoUrl = "https://github.com/tillkuhn/triptale";

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        grid.setPadding(new Insets(10));
        int row = 0;

        Label title = new Label("TripTale");
        title.setStyle("-fx-font-size: 16; -fx-font-weight: bold;");
        grid.add(title, 0, row++, 2, 1);

        Label desc = new Label("Offline-first cycling and hiking trip diary with git sync");
        desc.setWrapText(true);
        desc.setMaxWidth(360);
        grid.add(desc, 0, row++, 2, 1);

        grid.add(new Label("Version:"), 0, row);
        grid.add(new Label(version), 1, row++);
        grid.add(new Label("Built:"), 0, row);
        grid.add(new Label(builtAt), 1, row++);
        grid.add(new Label("Runtime:"), 0, row);
        grid.add(new Label("Java " + javaVersion + "  ·  JavaFX " + javafxVersion), 1, row++);
        Runtime runtime = Runtime.getRuntime();
        long usedBytes = runtime.totalMemory() - runtime.freeMemory();
        String usedMemory = String.format(Locale.ROOT, "%.0f MB", usedBytes / (1024.0 * 1024.0));
        grid.add(new Label("Memory:"), 0, row);
        grid.add(new Label(usedMemory), 1, row++);
        grid.add(new Label("License:"), 0, row);
        grid.add(new Label("Apache 2.0"), 1, row++);
        grid.add(new Label("Source:"), 0, row);
        Hyperlink link = new Hyperlink("github.com/tillkuhn/triptale");
        link.setOnAction(e -> openInBrowser(repoUrl));
        grid.add(link, 1, row);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About TripTale");
        alert.setHeaderText(null);
        alert.getDialogPane().setContent(grid);
        alert.getButtonTypes().setAll(ButtonType.CLOSE);
        applyStylesheet(alert.getDialogPane());
        alert.showAndWait();
    }

    @FXML
    public void onOpenTrackUrl() {
        String url = trackUrlField.getText();
        if (isValidHttpUrl(url)) openInBrowser(url.trim());
    }

    @FXML
    public void onEditPreferences() {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Edit Preferences");
        dlg.setHeaderText("Local preferences (not synced via git)");

        TextField patternField = new TextField(store.getImpressionsFilePattern().orElse(""));
        patternField.setPromptText("e.g. ${HOME}/Pictures/00_Faves/output/${DATE}*.jpg");
        patternField.setPrefColumnCount(36);
        TextField columnsField = new TextField(Integer.toString(store.getImpressionsGridColumns()));
        columnsField.setPrefColumnCount(4);
        TextField favePatternField = new TextField(store.getImpressionsFaveFilePattern().orElse(""));
        favePatternField.setPromptText("e.g. ${HOME}/Pictures/00_Faves/${DATE}*.jpg");
        favePatternField.setPrefColumnCount(36);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(14));
        grid.getStyleClass().add("card");
        grid.add(new Label("Impressions file pattern:"), 0, 0);
        grid.add(patternField, 1, 0);
        grid.add(new Label("Impressions grid columns:"), 0, 1);
        grid.add(columnsField, 1, 1);
        grid.add(new Label("Faves file pattern:"), 0, 2);
        grid.add(favePatternField, 1, 2);

        dlg.getDialogPane().setContent(grid);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        applyStylesheet(dlg.getDialogPane());

        Optional<ButtonType> result = dlg.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        store.setImpressionsFilePattern(patternField.getText().trim());
        int columns;
        try {
            columns = Integer.parseInt(columnsField.getText().trim());
            if (columns < 1) columns = 1;
        } catch (NumberFormatException nfe) {
            columns = 2;
        }
        store.setImpressionsGridColumns(columns);
        store.setImpressionsFaveFilePattern(favePatternField.getText().trim());
        updateImpressionsButton(datePicker.getValue());
        updateFavesButton(datePicker.getValue());
        status("Preferences saved");
    }

    @FXML
    public void onShowImpressions() {
        Trip trip = tripCombo.getValue();
        LocalDate date = datePicker.getValue();
        if (trip == null || date == null) return;
        String pattern = store.getImpressionsFilePattern().orElse(null);
        if (pattern == null) return;
        List<Path> images = impressionsResolver.resolve(pattern, date);
        if (images.isEmpty()) return;
        showImagePopup("Impressions", images, date);
    }

    @FXML
    public void onShowFaves() {
        Trip trip = tripCombo.getValue();
        LocalDate date = datePicker.getValue();
        if (trip == null || date == null) return;
        String pattern = store.getImpressionsFaveFilePattern().orElse(null);
        if (pattern == null) return;
        List<Path> images = impressionsResolver.resolve(pattern, date);
        if (images.isEmpty()) return;
        showImagePopup("Faves", images, date);
    }

    private void showImagePopup(String title, List<Path> images, LocalDate date) {
        int[] index = {0};
        Map<Path, ExifInfo> exifCache = new HashMap<>();

        ImageView imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(640);
        imageView.setFitHeight(480);
        Label counter = new Label();

        Label filenameLabel = new Label();
        filenameLabel.setStyle("-fx-font-weight: bold;");
        Label metaLabel = new Label();
        VBox topInfo = new VBox(2, filenameLabel, metaLabel);
        topInfo.setAlignment(javafx.geometry.Pos.CENTER);

        Button firstBtn = new Button("⏮");
        Button prevBtn = new Button("◀");
        Button nextBtn = new Button("▶");
        Button lastBtn = new Button("⏭");

        ButtonType mapButtonType = new ButtonType("Open in Maps", ButtonBar.ButtonData.LEFT);

        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle(title);
        dlg.setResizable(true);
        dlg.getDialogPane().getButtonTypes().add(mapButtonType);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        Button mapButton = (Button) dlg.getDialogPane().lookupButton(mapButtonType);
        Tooltip mapTooltip = new Tooltip();
        Tooltip.install(mapButton, mapTooltip);

        Runnable refresh = () -> {
            Path p = images.get(index[0]);
            imageView.setImage(new Image(p.toUri().toString(), 640, 480, true, true, true));
            counter.setText((index[0] + 1) + " / " + images.size());
            firstBtn.setDisable(index[0] == 0);
            prevBtn.setDisable(index[0] == 0);
            nextBtn.setDisable(index[0] == images.size() - 1);
            lastBtn.setDisable(index[0] == images.size() - 1);

            filenameLabel.setText(p.getFileName().toString());
            ExifInfo exif = exifCache.computeIfAbsent(p, exifReader::read);
            if (exif.hasCameraData()) {
                StringBuilder sb = new StringBuilder();
                if (exif.cameraModel() != null) sb.append(exif.cameraModel());
                if (exif.aperture() != null) {
                    if (sb.length() > 0) sb.append(" · ");
                    sb.append(exif.aperture());
                }
                if (exif.exposureTime() != null) {
                    if (sb.length() > 0) sb.append(" · ");
                    sb.append(exif.exposureTime());
                }
                metaLabel.setText(sb.toString());
            } else {
                metaLabel.setText("No camera data");
            }

            if (exif.hasLocation()) {
                mapButton.setDisable(false);
                mapTooltip.setText("Open coordinates in Google Maps");
            } else {
                mapButton.setDisable(true);
                mapTooltip.setText("No geo data");
            }
        };
        firstBtn.setOnAction(ev -> { index[0] = 0; refresh.run(); });
        prevBtn.setOnAction(ev -> { if (index[0] > 0) index[0]--; refresh.run(); });
        nextBtn.setOnAction(ev -> { if (index[0] < images.size() - 1) index[0]++; refresh.run(); });
        lastBtn.setOnAction(ev -> { index[0] = images.size() - 1; refresh.run(); });

        mapButton.addEventFilter(ActionEvent.ACTION, ev -> {
            ExifInfo exif = exifCache.get(images.get(index[0]));
            if (exif != null && exif.hasLocation()) {
                openInBrowser(exif.mapsUrl());
            }
            ev.consume();
        });

        refresh.run();

        HBox nav = new HBox(8, firstBtn, prevBtn, counter, nextBtn, lastBtn);
        nav.setAlignment(javafx.geometry.Pos.CENTER);
        VBox content = new VBox(10, topInfo, imageView, nav);
        content.setAlignment(javafx.geometry.Pos.CENTER);

        dlg.setHeaderText(date.toString());
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
            if (ev.getCode() == KeyCode.LEFT) {
                if (index[0] > 0) { index[0]--; refresh.run(); }
                ev.consume();
            } else if (ev.getCode() == KeyCode.RIGHT) {
                if (index[0] < images.size() - 1) { index[0]++; refresh.run(); }
                ev.consume();
            }
        });
        applyStylesheet(dlg.getDialogPane());
        dlg.showAndWait();
    }

    /** True when the value is a well-formed absolute http(s) URL. */
    private static boolean isValidHttpUrl(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            java.net.URI uri = java.net.URI.create(value.trim());
            String scheme = uri.getScheme();
            return uri.isAbsolute()
                    && scheme != null
                    && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void openInBrowser(String url) {
        try {
            if (hostServices != null) {
                hostServices.showDocument(url);
            } else {
                log.warn("HostServices not available; cannot open {}", url);
            }        } catch (Exception ex) {
            log.warn("Could not open browser for {}: {}", url, ex.getMessage());
        }
    }

    private Double parseDouble(String s, String field) {
        if (s == null || s.isBlank()) return null;
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (NumberFormatException nfe) {
            error("Invalid " + field + ": " + s);
            return null;
        }
    }

    private void status(String msg) {
        log.info(msg);
        if (statusLabel != null) statusLabel.setText(msg);
    }

    private void error(String msg) {
        log.warn(msg);
        if (statusLabel != null) statusLabel.setText(msg);
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        applyStylesheet(a.getDialogPane());
        a.showAndWait();
    }

    private static String describe(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable t = e;
        while (t != null) {
            if (sb.length() > 0) sb.append(" — caused by ");
            String m = t.getMessage();
            sb.append(t.getClass().getSimpleName())
                    .append(m == null ? "" : ": " + m);
            t = t.getCause();
        }
        return sb.toString();
    }

    private void applyStylesheet(javafx.scene.control.DialogPane pane) {
        pane.getStylesheets().add(
                getClass().getResource("/fxml/triptale.css").toExternalForm());
    }
}
