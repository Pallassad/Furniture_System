package furniture_system.controller;

import furniture_system.model.FurnitureType;
import furniture_system.service.FurnitureTypeService;
import furniture_system.utils.NotificationUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class FurnitureTypeController {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ── Table ──────────────────────────────────────────────────────────────
    @FXML private TableView<FurnitureType>            typeTable;
    @FXML private TableColumn<FurnitureType, Integer> colId;
    @FXML private TableColumn<FurnitureType, String>  colName;
    @FXML private TableColumn<FurnitureType, String>  colDescription;
    @FXML private TableColumn<FurnitureType, String>  colStatus;
    @FXML private TableColumn<FurnitureType, String>  colCreatedAt;

    // ── Search & Toolbar ───────────────────────────────────────────────────
    @FXML private TextField txtSearch;
    @FXML private Button    btnAdd;
    @FXML private Button    btnEdit;
    @FXML private Button    btnDelete;
    @FXML private Button    btnRefresh;
    @FXML private Label     statusBarLabel;

    private final FurnitureTypeService             service = new FurnitureTypeService();
    private final ObservableList<FurnitureType>    data    = FXCollections.observableArrayList();

    // ══════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        setupColumns();
        loadData();

        typeTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> {
                    boolean has = sel != null;
                    btnEdit.setDisable(!has);
                    btnDelete.setDisable(!has);
                });
        btnEdit.setDisable(true);
        btnDelete.setDisable(true);
    }

    // ── Column setup ───────────────────────────────────────────────────────
    private void setupColumns() {
        colId.setCellValueFactory(c ->
                new javafx.beans.property.SimpleIntegerProperty(c.getValue().getTypeId()).asObject());
        colName.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getTypeName()));
        colDescription.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getDescription() != null
                        ? c.getValue().getDescription() : "—"));
        colStatus.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getStatus()));
        colCreatedAt.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getCreatedAt() != null
                        ? c.getValue().getCreatedAt().format(DATE_FMT) : "—"));

        // Status color
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); setStyle(""); return; }
                setText(s);
                setStyle("ACTIVE".equals(s)
                        ? "-fx-text-fill:#27ae60;-fx-font-weight:bold;"
                        : "-fx-text-fill:#c62828;-fx-font-weight:bold;");
            }
        });

        // Row color for INACTIVE
        typeTable.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(FurnitureType item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setStyle(""); return; }
                setStyle("INACTIVE".equals(item.getStatus())
                        ? "-fx-background-color:#e8e8e8;" : "");
            }
        });

        typeTable.setItems(data);
    }

    // ── Load & Search ──────────────────────────────────────────────────────
    private void loadData() {
        try {
            List<FurnitureType> list = service.getAll();
            data.setAll(list);
            setStatus("Loaded " + list.size() + " furniture type(s).");
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Error", "Failed to load data: " + e.getMessage());
        }
    }

    @FXML public void handleRefresh() { txtSearch.clear(); loadData(); }

    @FXML public void handleSearch() {
        try {
            List<FurnitureType> list = service.search(txtSearch.getText().trim());
            data.setAll(list);
            setStatus("Found " + list.size() + " furniture type(s).");
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Search Error", e.getMessage());
        }
    }

    // ==================== ADD ====================
    @FXML
    public void handleAdd() {
        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Add New Furniture Type");

        TextField        tfName   = new TextField();
        TextArea         taDesc   = new TextArea();
        ComboBox<String> cbStatus = new ComboBox<>(FXCollections.observableArrayList("ACTIVE", "INACTIVE"));
        cbStatus.setValue("ACTIVE"); cbStatus.setMaxWidth(Double.MAX_VALUE);
        taDesc.setPrefRowCount(4); taDesc.setWrapText(true);

        Label lblErr = new Label();
        lblErr.setStyle("-fx-text-fill:#c62828;-fx-font-size:12px;"); lblErr.setWrapText(true);

        GridPane grid = buildGrid();
        int row = 0;
        grid.add(fl("Type Name *"),  0, row); grid.add(tfName,   1, row++);
        grid.add(new Label(""),      0, row); grid.add(hint("2–100 characters, must be unique."), 1, row++);
        grid.add(fl("Status *"),     0, row); grid.add(cbStatus, 1, row++);
        grid.add(new Separator(),    0, row, 2, 1); row++;
        grid.add(fl("Description"),  0, row); grid.add(taDesc,   1, row++);
        grid.add(new Label(""),      0, row); grid.add(hint("Optional. Short description of this category."), 1, row++);
        grid.add(new Separator(),    0, row, 2, 1); row++;
        grid.add(lblErr,             0, row, 2, 1); row++;

        Button btnSave   = primaryBtn("Add Type");
        Button btnCancel = new Button("Cancel");
        btnCancel.setStyle("-fx-background-radius:6;-fx-padding:8 18;");
        HBox btns = new HBox(10, btnCancel, btnSave); btns.setAlignment(Pos.CENTER_RIGHT);
        grid.add(btns, 0, row, 2, 1);

        btnSave.setOnAction(ev -> {
            lblErr.setText("");
            try {
                String err = service.add(tfName.getText(), taDesc.getText(), cbStatus.getValue());
                if (err != null) { lblErr.setText(err); return; }
                setStatus("Furniture type [" + tfName.getText().trim() + "] added.");
                NotificationUtil.success(typeTable, "Type added: " + tfName.getText().trim());
                loadData(); dlg.close();
            } catch (SQLException ex) { lblErr.setText("Database error: " + ex.getMessage()); }
        });
        btnCancel.setOnAction(ev -> dlg.close());

        dlg.setScene(new Scene(grid, 480, 360));
        dlg.setResizable(false); dlg.showAndWait();
    }

    // ==================== EDIT ====================
    @FXML
    public void handleEdit() {
        FurnitureType sel = typeTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Edit Furniture Type - " + sel.getTypeName());

        TextField        tfName   = new TextField(sel.getTypeName());
        TextArea         taDesc   = new TextArea(sel.getDescription() != null ? sel.getDescription() : "");
        ComboBox<String> cbStatus = new ComboBox<>(FXCollections.observableArrayList("ACTIVE", "INACTIVE"));
        cbStatus.setValue(sel.getStatus()); cbStatus.setMaxWidth(Double.MAX_VALUE);
        taDesc.setPrefRowCount(4); taDesc.setWrapText(true);

        Label lblInfo = new Label("Type ID: " + sel.getTypeId()
                + "   |   Created: " + (sel.getCreatedAt() != null ? sel.getCreatedAt().format(DATE_FMT) : "—"));
        lblInfo.setStyle("-fx-text-fill:#555;-fx-font-size:12px;");

        Label lblErr = new Label();
        lblErr.setStyle("-fx-text-fill:#c62828;-fx-font-size:12px;"); lblErr.setWrapText(true);

        GridPane grid = buildGrid();
        int row = 0;
        grid.add(lblInfo,            0, row, 2, 1); row++;
        grid.add(new Separator(),    0, row, 2, 1); row++;
        grid.add(fl("Type Name *"),  0, row); grid.add(tfName,   1, row++);
        grid.add(fl("Status *"),     0, row); grid.add(cbStatus, 1, row++);
        grid.add(new Separator(),    0, row, 2, 1); row++;
        grid.add(fl("Description"),  0, row); grid.add(taDesc,   1, row++);
        grid.add(new Label(""),      0, row); grid.add(hint("Optional. Short description of this category."), 1, row++);
        grid.add(new Separator(),    0, row, 2, 1); row++;
        grid.add(lblErr,             0, row, 2, 1); row++;

        Button btnSave   = primaryBtn("Save Changes");
        Button btnCancel = new Button("Cancel");
        btnCancel.setStyle("-fx-background-radius:6;-fx-padding:8 18;");
        HBox btns = new HBox(10, btnCancel, btnSave); btns.setAlignment(Pos.CENTER_RIGHT);
        grid.add(btns, 0, row, 2, 1);

        btnSave.setOnAction(ev -> {
            lblErr.setText("");
            try {
                String err = service.update(sel.getTypeId(), tfName.getText(), taDesc.getText(), cbStatus.getValue());
                if (err != null) { lblErr.setText(err); return; }
                setStatus("Furniture type [" + tfName.getText().trim() + "] updated.");
                NotificationUtil.success(typeTable, "Type updated.");
                loadData(); dlg.close();
            } catch (SQLException ex) { lblErr.setText("Database error: " + ex.getMessage()); }
        });
        btnCancel.setOnAction(ev -> dlg.close());

        dlg.setScene(new Scene(grid, 480, 380));
        dlg.setResizable(false); dlg.showAndWait();
    }

    // ==================== DELETE ====================
    @FXML
    public void handleDelete() {
        FurnitureType sel = typeTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Permanently delete furniture type \"" + sel.getTypeName() + "\" (ID: " + sel.getTypeId() + ")?\n\n"
                + "⚠ Requirement: no products (including INACTIVE) remain linked to this type.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Delete"); confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.YES) return;
            try {
                String err = service.delete(sel.getTypeId());
                if (err != null) { alert(Alert.AlertType.WARNING, "Cannot Delete", err); return; }
                setStatus("Deleted [" + sel.getTypeName() + "].");
            NotificationUtil.warning(typeTable, "Deleted: " + sel.getTypeName());
                loadData();
            } catch (SQLException ex) {
                alert(Alert.AlertType.ERROR, "Delete Failed", ex.getMessage());
            }
        });
    }

    // ── UI helpers ─────────────────────────────────────────────────────────
    private GridPane buildGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(10); grid.setPadding(new Insets(24));
        grid.getColumnConstraints().addAll(new ColumnConstraints(130), new ColumnConstraints(290));
        return grid;
    }

    private Label fl(String t)   { Label l = new Label(t); l.setStyle("-fx-font-weight:bold;-fx-font-size:12px;"); return l; }
    private Label hint(String t) { Label l = new Label(t); l.setStyle("-fx-font-size:10px;-fx-text-fill:#888;"); return l; }
    private Button primaryBtn(String t) {
        Button b = new Button(t);
        b.setStyle("-fx-background-color:#3949ab;-fx-text-fill:white;-fx-background-radius:6;-fx-padding:8 18;-fx-font-weight:bold;");
        return b;
    }
    private void setStatus(String msg) { setStatus(msg, false); }
    private void setStatus(String msg, boolean isError) {
        if (statusBarLabel == null) return;
        statusBarLabel.setText(msg);
        if (isError) {
            statusBarLabel.setStyle("-fx-text-fill:#c0392b;-fx-font-weight:bold;");
        } else if (msg.startsWith("✔") || msg.contains("added") || msg.contains("updated")
                || msg.contains("deleted") || msg.contains("created") || msg.contains("saved")
                || msg.contains("recorded") || msg.contains("linked") || msg.contains("success")) {
            statusBarLabel.setStyle("-fx-text-fill:#1e7e4a;-fx-font-weight:bold;");
        } else {
            statusBarLabel.setStyle("-fx-text-fill:#6878aa;-fx-font-weight:normal;");
        }
    }
    private void alert(Alert.AlertType t, String title, String msg) {
        Alert a = new Alert(t); a.setTitle(title); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }
}