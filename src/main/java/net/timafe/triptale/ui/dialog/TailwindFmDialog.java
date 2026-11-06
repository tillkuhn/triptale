package net.timafe.triptale.ui.dialog;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.collections.FXCollections;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import java.util.Comparator;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import javafx.stage.Window;
import net.timafe.triptale.radio.Station;
import net.timafe.triptale.radio.Track;
import net.timafe.triptale.ui.EqualizerIcon;
import net.timafe.triptale.ui.RadioPlayer;
import net.timafe.triptale.ui.UiText;

/**
 * Small, non-modal Tailwind FM window (a plain {@link Stage}) (todo 46): logo, now-playing and DJ line, and the
 * transport buttons. Stays open while the user keeps writing; it observes the
 * {@link RadioPlayer} and refreshes itself on every state change. Holds no state of its own.
 */
public final class TailwindFmDialog {

    /** Displayed width; the source image is 1000px wide so it stays sharp on Retina. */
    private static final double LOGO_WIDTH = 400;
    private static final double DJ_LINE_HEIGHT = 20;
    private static final double TABLE_HEIGHT = 180;
    private static final double TRANSPORT_BUTTON_WIDTH = 44;
    private static final double CLOSE_BUTTON_WIDTH = 84;

    private final RadioPlayer radio;
    private final Stage stage = new Stage();
    private final Label nowPlaying = new Label();
    private final Label djLine = new Label();
    private final Button playPause = new Button();
    private final TableView<Track> table = new TableView<>();
    private final TitledPane tracksPane = new TitledPane();
    private VBox box;
    private final EqualizerIcon equalizer = new EqualizerIcon(18, 4, 3, 22);
    private final javafx.collections.ObservableList<Track> trackItems = FXCollections.observableArrayList();
    private final Runnable refresh = this::refresh;

    public TailwindFmDialog(RadioPlayer radio, Window owner) {
        this.radio = radio;

        ImageView logo = new ImageView(new Image(
                TailwindFmDialog.class.getResource("/images/tailwind-fm.png").toExternalForm(),
                LOGO_WIDTH * 2, 0, true, true));
        logo.setFitWidth(LOGO_WIDTH);
        logo.setPreserveRatio(true);
        logo.setSmooth(true);

        // Single lines, cut with an ellipsis: wrapping would grow the content past the fixed-size
        // stage and clip the buttons. The prefWidth also keeps a long text from widening the window.
        for (Label l : new Label[]{nowPlaying, djLine}) {
            l.setWrapText(false);
            l.setTextOverrun(OverrunStyle.ELLIPSIS);
            l.setMinWidth(0);
            l.setPrefWidth(LOGO_WIDTH);
            l.setMaxWidth(LOGO_WIDTH);
        }
        nowPlaying.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        djLine.setStyle("-fx-font-style: italic; -fx-opacity: 0.8;");
        // Reserve the DJ line's row at all times, so the buttons don't jump when it comes and goes.
        djLine.setMinHeight(DJ_LINE_HEIGHT);
        djLine.setPrefHeight(DJ_LINE_HEIGHT);
        djLine.setMaxHeight(DJ_LINE_HEIGHT);
        djLine.setAlignment(Pos.TOP_LEFT);

        // Tape recorder symbols only; one fixed width so the row doesn't jump when Play becomes Pause.
        playPause.setOnAction(e -> radio.togglePause());
        playPause.setTooltip(new Tooltip("Play / Pause"));
        Button next = new Button("⏭");
        next.setTooltip(new Tooltip("Next random track"));
        next.setOnAction(e -> radio.playRandom());
        Button stop = new Button("⏹");
        stop.setTooltip(new Tooltip("Stop"));
        stop.setOnAction(e -> radio.stop());
        Button close = new Button("Close");
        close.setOnAction(e -> stage.hide());
        for (Button b : new Button[]{playPause, next, stop}) {
            b.setMinWidth(TRANSPORT_BUTTON_WIDTH);
            b.setPrefWidth(TRANSPORT_BUTTON_WIDTH);
        }
        close.setMinWidth(CLOSE_BUTTON_WIDTH);
        close.setPrefWidth(CLOSE_BUTTON_WIDTH);
        // Winamp-style strip in the gap between the transport buttons and Close; the bars only
        // show and move while a track plays. 18 bars of 4px + 3px gaps = 123px of the ~150px free.
        HBox spacer = new HBox(equalizer);
        spacer.setAlignment(Pos.CENTER);
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(8, playPause, next, stop, spacer, close);
        buttons.setAlignment(Pos.CENTER_LEFT);

        buildTrackList();
        box = new VBox(8, logo, nowPlaying, djLine, buttons, tracksPane);
        box.setPadding(new Insets(12));

        // A plain Stage rather than a Dialog: no button bar, so Close can sit in our own row.
        Scene scene = new Scene(box);
        scene.getStylesheets().add(
                TailwindFmDialog.class.getResource("/fxml/triptale.css").toExternalForm());
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) stage.hide();
        });
        stage.initOwner(owner); // non-modal by default
        stage.setTitle(Station.NAME);
        stage.setScene(scene);
        stage.setResizable(false);
        stage.setOnShown(e -> {
            radio.addChangeListener(refresh);
            refresh();
        });
        stage.setOnHidden(e -> {
            radio.removeChangeListener(refresh);
            equalizer.stop(); // no animation ticks while the window is closed
        });
        refresh();
    }

    /** Foldable track list (collapsed by default); a click on a row plays that track. */
    private void buildTrackList() {
        TableColumn<Track, String> name = new TableColumn<>("Title");
        name.setCellValueFactory(c -> new javafx.beans.property.ReadOnlyStringWrapper(c.getValue().title()));
        name.setComparator(String.CASE_INSENSITIVE_ORDER);
        TableColumn<Track, Track> size = new TableColumn<>("Size");
        size.setCellValueFactory(c -> new javafx.beans.property.ReadOnlyObjectWrapper<>(c.getValue()));
        size.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(Track t, boolean empty) {
                super.updateItem(t, empty);
                setText(empty || t == null ? null : UiText.fileSize(t.size()));
                setAlignment(Pos.CENTER_RIGHT);
            }
        });
        size.setComparator(Comparator.comparingLong(Track::size)); // by bytes, not by the shown text
        size.setMinWidth(80);
        size.setMaxWidth(80);
        // The column divider otherwise turns the header into a resize handle that swallows sort clicks.
        for (TableColumn<Track, ?> c : java.util.List.of(name, size)) {
            c.setResizable(false);
            c.setReorderable(false);
        }
        table.getColumns().add(name);
        table.getColumns().add(size);
        // Fixed columns: the title takes what the size column and the scrollbar leave over.
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        name.prefWidthProperty().bind(table.widthProperty().subtract(80 + 22));
        // Sort through a SortedList instead of re-sorting the items in place: stays consistent
        // when the items are reloaded or the table refreshes.
        var sorted = new javafx.collections.transformation.SortedList<>(trackItems);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        table.getSortOrder().add(name);
        table.setPrefHeight(TABLE_HEIGHT);
        table.setMinHeight(TABLE_HEIGHT);
        table.setPlaceholder(new Label("No tracks in the radio folder"));
        table.setRowFactory(tv -> {
            TableRow<Track> row = new TableRow<>() {
                @Override
                protected void updateItem(Track t, boolean empty) {
                    super.updateItem(t, empty);
                    boolean now = !empty && t != null && radio.currentTrack() != null
                            && t.file().equals(radio.currentTrack().file());
                    setStyle(now ? "-fx-font-weight: bold;" : "");
                }
            };
            row.setOnMouseClicked(e -> {
                if (!row.isEmpty()) radio.playTrack(row.getItem());
            });
            return row;
        });

        tracksPane.setText("Record Crate");
        tracksPane.setContent(table);
        tracksPane.setAnimated(false); // the height must be final when we re-fit the window
        tracksPane.setExpanded(false);
        tracksPane.setPrefWidth(LOGO_WIDTH);
        tracksPane.setMaxWidth(LOGO_WIDTH);
        tracksPane.expandedProperty().addListener((o, was, expanded) -> {
            if (expanded) reloadTracks();
            // Later: the TitledPane skin updates its own height from this same property change,
            // and may not have done so yet when our listener runs.
            javafx.application.Platform.runLater(this::fitHeight);
        });
    }

    /**
     * Re-fits the fixed-size stage to its content after folding/unfolding. Sets the height
     * explicitly: {@code sizeToScene()} and {@code setHeight} are ignored by a non-resizable stage.
     * The width stays untouched.
     */
    private void fitHeight() {
        if (!stage.isShowing()) return;
        double chrome = stage.getHeight() - stage.getScene().getHeight();
        box.applyCss();
        box.layout();
        // A non-resizable stage ignores programmatic size changes on macOS: lift the lock briefly.
        stage.setResizable(true);
        stage.setHeight(chrome + box.prefHeight(box.getWidth()));
        stage.setResizable(false);
    }

    private void reloadTracks() {
        try {
            var tracks = radio.tracks();
            trackItems.setAll(tracks);
            tracksPane.setText("Record Crate (" + tracks.size() + ")");
        } catch (RuntimeException e) {
            tracksPane.setText("Record Crate (unavailable)");
        }
    }

    /** Shows the window, or brings it to the front if it is already open. */
    public void show() {
        refresh();
        if (stage.isShowing()) {
            stage.toFront();
            stage.requestFocus();
        } else {
            stage.show();
        }
    }

    private void refresh() {
        boolean loaded = radio.isLoaded();
        nowPlaying.setText(loaded ? "📻 " + radio.label() : "📻 Off air");
        nowPlaying.setTooltip(loaded ? new Tooltip(radio.label()) : null);
        String dj = radio.djLine();
        String spoken = dj.isEmpty() ? "" : radio.djName() + ": “" + dj + "”";
        djLine.setText(spoken);
        djLine.setTooltip(dj.isEmpty() ? null : new Tooltip(spoken));
        playPause.setText(radio.isPlaying() ? "⏸" : "▶");
        boolean playing = radio.isPlaying();
        equalizer.setVisible(playing);
        if (playing && stage.isShowing()) equalizer.start(); else equalizer.stop();
        table.refresh(); // re-evaluates which row is the current track
    }
}
