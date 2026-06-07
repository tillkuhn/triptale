package net.timafe.triptale.ui;

import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.collections.FXCollections;
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
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ButtonBar;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.GridPane;
import javafx.event.ActionEvent;
import javafx.util.StringConverter;
import net.timafe.triptale.config.TripTaleProperties;
import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.export.DiaryExporter;
import net.timafe.triptale.git.GitService;
import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.util.Slugs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Component
public class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    @FXML private ComboBox<Trip> tripCombo;
    @FXML private DatePicker datePicker;
    @FXML private TextField distanceField;
    @FXML private TextField altField;
    @FXML private TextField routeField;
    @FXML private TextArea notesArea;
    @FXML private Label statusLabel;
    @FXML private Label tourDayLabel;
    @FXML private Button saveButton;
    @FXML private Button commitButton;
    @FXML private Button prevDayButton;
    @FXML private Button firstDayButton;
    @FXML private Button todayButton;

    private static final String CREATE = "Create";
    private static final String UPDATE = "Update";
    private static final int COMMIT_MSG_INLINE_LIMIT = 5;

    private String baselineDistance = "";
    private String baselineAlt = "";
    private String baselineRoute = "";
    private String baselineNotes = "";
    private boolean entryExists;

    private final Map<String, String> pending = new LinkedHashMap<>();

    private final MarkdownStore store;
    private final GitService gitService;
    private final TripTaleProperties props;
    private final DiaryExporter diaryExporter;
    private final BuildProperties buildProperties;
    private final HostServices hostServices;

    public MainController(MarkdownStore store, GitService gitService, TripTaleProperties props,
                          DiaryExporter diaryExporter,
                          ObjectProvider<BuildProperties> buildPropertiesProvider,
                          ObjectProvider<HostServices> hostServicesProvider) {
        this.store = store;
        this.gitService = gitService;
        this.props = props;
        this.diaryExporter = diaryExporter;
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
            if (sel == null) return;
            if (sel.startDate() != null && datePicker.getValue() != null
                    && datePicker.getValue().isBefore(sel.startDate())) {
                datePicker.setValue(sel.startDate());
                status("Snapped to day 1 (" + sel.startDate() + ")");
                return;
            }
            loadEntry();
            updatePrevButtonState();
        });
        datePicker.setValue(LocalDate.now());
        datePicker.valueProperty().addListener((obs, old, sel) -> {
            Trip trip = tripCombo.getValue();
            if (sel != null && trip != null && trip.startDate() != null
                    && sel.isBefore(trip.startDate())) {
                datePicker.setValue(trip.startDate());
                status("Snapped to day 1 (" + trip.startDate() + ")");
                return;
            }
            loadEntry();
            updatePrevButtonState();
        });
        distanceField.textProperty().addListener((o, a, b) -> updateDirty());
        altField.textProperty().addListener((o, a, b) -> updateDirty());
        routeField.textProperty().addListener((o, a, b) -> updateDirty());
        notesArea.textProperty().addListener((o, a, b) -> updateDirty());
        if (!tripCombo.getItems().isEmpty()) {
            tripCombo.getSelectionModel().selectFirst();
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
            }
        });
        updateCommitButton();
        status("Data dir: " + props.resolvedDataDir());
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
        grid.setPadding(new Insets(10));
        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Start date:"), 0, 1);
        grid.add(startField, 1, 1);
        grid.add(new Label("Description:"), 0, 2);
        grid.add(descArea, 1, 2);

        dlg.getDialogPane().setContent(grid);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

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
        for (LocalDate d : dates) {
            DiaryEntry e = store.loadEntry(trip.slug(), d);
            if (e.distance() != null) totalDistance += e.distance();
            if (e.altitudeMeters() != null) totalAltitude += e.altitudeMeters();
        }

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        int row = 0;
        grid.add(new Label("Slug:"), 0, row);
        grid.add(new Label(trip.slug()), 1, row++);
        grid.add(new Label("Start date:"), 0, row);
        grid.add(new Label(trip.startDate() == null ? "—" : trip.startDate().toString()), 1, row++);
        grid.add(new Label("Entries:"), 0, row);
        grid.add(new Label(Integer.toString(dates.size())), 1, row++);
        grid.add(new Label("Total distance:"), 0, row);
        grid.add(new Label(String.format(Locale.ROOT, "%.1f km", totalDistance)), 1, row++);
        grid.add(new Label("Total altitude:"), 0, row);
        grid.add(new Label(String.format(Locale.ROOT, "%.0f m", totalAltitude)), 1, row++);
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

        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Export Diary");
        dlg.setHeaderText(trip.name());
        dlg.setResizable(true);
        dlg.getDialogPane().setContent(ta);

        ButtonType copyType = new ButtonType("Copy", ButtonBar.ButtonData.OTHER);
        dlg.getDialogPane().getButtonTypes().setAll(copyType, ButtonType.CLOSE);

        Button copyBtn = (Button) dlg.getDialogPane().lookupButton(copyType);
        copyBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(exported);
            Clipboard.getSystemClipboard().setContent(cc);
            status("Diary copied to clipboard");
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
        notesArea.setText(e.notes() == null ? "" : e.notes());
        snapshotBaseline();
        updateDirty();
    }

    private void snapshotBaseline() {
        baselineDistance = distanceField.getText();
        baselineAlt = altField.getText();
        baselineRoute = routeField.getText();
        baselineNotes = notesArea.getText();
    }

    private boolean isDirty() {
        return !Objects.equals(distanceField.getText(), baselineDistance)
                || !Objects.equals(altField.getText(), baselineAlt)
                || !Objects.equals(routeField.getText(), baselineRoute)
                || !Objects.equals(notesArea.getText(), baselineNotes);
    }

    private void updateDirty() {
        boolean dirty = isDirty();
        if (saveButton != null) {
            saveButton.setDisable(!dirty);
            saveButton.setText(entryExists ? "Save" : "Create");
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
        commitButton.setText("Commit (" + pending.size() + ")");
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
        Double distance = parseDouble(distanceField.getText(), "distance");
        Double alt = parseDouble(altField.getText(), "altitude");
        if (distance == null && !distanceField.getText().isBlank()) return;
        if (alt == null && !altField.getText().isBlank()) return;
        DiaryEntry entry = DiaryEntry.builder(date)
                .distance(distance)
                .altitudeMeters(alt)
                .route(routeField.getText())
                .notes(notesArea.getText())
                .build();
        boolean wasNew = !entryExists;
        store.saveEntry(trip.slug(), entry);
        entryExists = true;
        addPending(trip.slug() + "/" + date, wasNew ? CREATE : UPDATE);
        snapshotBaseline();
        updateDirty();
        status("Saved " + trip.slug() + "/" + date);
    }

    @FXML
    public void onCommit() {
        if (!canCommit()) return;
        String message = buildCommitMessage();
        try {
            gitService.commitAll(message);
        } catch (RuntimeException e) {
            error(e.getMessage());
            return;
        }
        int n = pending.size();
        pending.clear();
        updateCommitButton();
        status("Committed " + n + " change" + (n == 1 ? "" : "s"));
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
            error(e.getMessage());
        }
    }

    @FXML
    public void onPush() {
        try {
            gitService.push();
            status("Pushed to remote");
        } catch (RuntimeException e) {
            error(e.getMessage());
        }
    }

    @FXML
    public void onRemoteInfo() {
        String configuredRemote = props.getGit().getRemote();
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
        grid.add(new Label("Configured remote:"), 0, row);
        grid.add(new Label(configuredRemote.isBlank() ? "(none)" : configuredRemote), 1, row++);
        grid.add(new Label("Active origin URL:"), 0, row);
        grid.add(new Label(actualRemote.isBlank() ? "(none)" : actualRemote), 1, row++);
        grid.add(new Label("Author:"), 0, row);
        grid.add(new Label(authorDisplay), 1, row);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Remote Info");
        alert.setHeaderText("Git configuration");
        alert.getDialogPane().setContent(grid);
        alert.getButtonTypes().setAll(ButtonType.CLOSE);
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
                ? DateTimeFormatter.ofPattern("yyyy-MM-dd")
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
        alert.showAndWait();
    }

    private void openInBrowser(String url) {
        try {
            if (hostServices != null) {
                hostServices.showDocument(url);
            } else {
                log.warn("HostServices not available; cannot open {}", url);
            }
        } catch (Exception ex) {
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
        a.showAndWait();
    }
}
