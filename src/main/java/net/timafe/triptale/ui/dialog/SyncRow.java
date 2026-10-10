package net.timafe.triptale.ui.dialog;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * One step of a sync dialog (Smart Sync, Sync Tracks): checkbox, name, status text (+ extra
 * controls below it), status icon. Lives in a {@link #grid()} with four columns.
 */
final class SyncRow {

    final CheckBox check = new CheckBox();
    final Label name;
    final Label status = new Label();
    final VBox extra = new VBox(6);
    final ProgressIndicator spinner = new ProgressIndicator();
    final Label icon = new Label();
    private final boolean selectable;
    private final Runnable onLayoutChanged;

    /**
     * @param onCheckChanged  runs when the checkbox is toggled (e.g. to update the Sync button)
     * @param onLayoutChanged runs when the row's text or content changes, so the window can resize
     */
    SyncRow(String title, boolean selectable, Runnable onCheckChanged, Runnable onLayoutChanged) {
        this.selectable = selectable;
        this.onLayoutChanged = onLayoutChanged;
        name = new Label(title);
        name.setStyle("-fx-font-weight: bold;");
        status.setWrapText(true);
        status.setMaxWidth(Double.MAX_VALUE);
        status.setMinHeight(Region.USE_PREF_SIZE);
        spinner.setPrefSize(18, 18);
        spinner.setMaxSize(18, 18);
        check.selectedProperty().addListener((obs, was, is) -> onCheckChanged.run());
    }

    /** The grid sync rows are added to: checkbox, name, status (grows), icon. */
    static GridPane grid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(16));
        ColumnConstraints checkCol = new ColumnConstraints();
        ColumnConstraints nameCol = new ColumnConstraints(110);
        ColumnConstraints statusCol = new ColumnConstraints();
        statusCol.setHgrow(Priority.ALWAYS);
        statusCol.setFillWidth(true);
        ColumnConstraints iconCol = new ColumnConstraints(26);
        grid.getColumnConstraints().addAll(checkCol, nameCol, statusCol, iconCol);
        return grid;
    }

    void addTo(GridPane grid, int rowIndex) {
        if (selectable) grid.add(check, 0, rowIndex);
        grid.add(name, 1, rowIndex);
        VBox middle = new VBox(6, status, extra);
        grid.add(middle, 2, rowIndex);
        StackPane iconCell = new StackPane(spinner, icon);
        iconCell.setAlignment(Pos.TOP_CENTER);
        grid.add(iconCell, 3, rowIndex);
        for (Node n : List.of(check, name, middle, iconCell)) GridPane.setValignment(n, VPos.TOP);
        showNode(spinner, false);
    }

    void setEnabled(boolean enabled, boolean selected) {
        check.setDisable(!enabled);
        check.setSelected(enabled && selected);
    }

    void idle(String text) { show(text, "", "sync-status-idle"); }
    void muted(String text) { show(text, "–", "sync-status-muted"); }
    void ok(String text) { show(text, "✓", "sync-status-ok"); }
    void fail(String text) { show(text, "✗", "sync-status-error"); }

    void running(String text) {
        status.setText(text);
        status.getStyleClass().setAll("label", "sync-status-idle");
        showNode(icon, false);
        showNode(spinner, true);
        onLayoutChanged.run();
    }

    private void show(String text, String symbol, String styleClass) {
        status.setText(text);
        status.getStyleClass().setAll("label", styleClass);
        icon.setText(symbol);
        icon.getStyleClass().setAll("label", styleClass);
        showNode(spinner, false);
        showNode(icon, true);
        onLayoutChanged.run();
    }

    static void showNode(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
