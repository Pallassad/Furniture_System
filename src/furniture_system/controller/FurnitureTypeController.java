package furniture_system.controller;

import furniture_system.model.FurnitureType;
import furniture_system.service.FurnitureTypeService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;

import java.net.URL;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Controller for the Admin – Furniture Type Management screen.
 * Corresponding FXML: furniture_type_management.fxml
 */
public class FurnitureTypeController implements Initializable {

    // ── Table ─────────────────────────────────────────────────────────────────
    @FXML private TableView<FurnitureType>              tableView;
    @FXML private TableColumn<FurnitureType, Integer>   colId;
    @FXML private TableColumn<FurnitureType, String>    colName;
    @FXML private TableColumn<FurnitureType, String>    colDescription;
    @FXML private TableColumn<FurnitureType, String>    colStatus;
    @FXML private TableColumn<FurnitureType, String>    colCreatedAt;
    @FXML private TableColumn<FurnitureType, Void>      colActions;

    // ── Search ────────────────────────────────────────────────────────────────
    @FXML private TextField txtSearch;

    // ── Form ──────────────────────────────────────────────────────────────────
    @FXML private Label            lblFormTitle;
    @FXML private Label            lblError;
    @FXML private TextField        txtTypeName;
    @FXML private TextArea         txtDescription;
    @FXML private ComboBox<String> cmbStatus;
    @FXML private Button           btnSave;
    @FXML private Button           btnDelete;
    @FXML private Button           btnClear;

    // ── State ─────────────────────────────────────────────────────────────────
    private final FurnitureTypeService         service = new FurnitureTypeService();
    private final ObservableList<FurnitureType> data   = FXCollections.observableArrayList();
    private Integer editingId = null;   // null → Add mode

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm");

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupTable();
        cmbStatus.setItems(FXCollections.observableArrayList("ACTIVE", "INACTIVE"));
        cmbStatus.setValue("ACTIVE");
        btnDelete.setDisable(true); // disabled until a record is selected for editing
        loadData();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  TABLE SETUP
    // ════════════════════════════════════════════════════════════════════════
    private void setupTable() {
        colId.setCellValueFactory(new PropertyValueFactory<>("typeId"));
        colName.setCellValueFactory(new PropertyValueFactory<>("typeName"));
        colDescription.setCellValueFactory(new PropertyValueFactory<>("description"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        // CreatedAt column – format DATETIME2 → MM/dd/yyyy HH:mm
        colCreatedAt.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty) { setText(null); return; }
                FurnitureType ft = getTableView().getItems().get(getIndex());
                setText(ft.getCreatedAt() != null
                        ? ft.getCreatedAt().format(DATE_FMT) : "");
            }
        });

        // Color badge for Status column
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) { setText(null); setStyle(""); return; }
                setText(status);
                setStyle("ACTIVE".equals(status)
                        ? "-fx-text-fill:#27ae60;-fx-font-weight:bold;"
                        : "-fx-text-fill:#e74c3c;-fx-font-weight:bold;");
            }
        });

        // Actions column: Edit + Deactivate buttons
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnEdit  = new Button("✏ Edit");
            private final Button btnDeact = new Button("🚫 Deactivate");
            private final HBox   box      = new HBox(6, btnEdit, btnDeact);
            {
                btnEdit.setStyle(
                    "-fx-background-color:#3498db;-fx-text-fill:white;" +
                    "-fx-background-radius:4;-fx-cursor:hand;-fx-font-size:11;");
                btnDeact.setStyle(
                    "-fx-background-color:#e74c3c;-fx-text-fill:white;" +
                    "-fx-background-radius:4;-fx-cursor:hand;-fx-font-size:11;");

                btnEdit.setOnAction(e ->
                        populateForm(getTableView().getItems().get(getIndex())));
                btnDeact.setOnAction(e ->
                        handleDeactivate(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                FurnitureType ft = getTableView().getItems().get(getIndex());
                // Hide Deactivate button if already INACTIVE
                btnDeact.setVisible(!"INACTIVE".equals(ft.getStatus()));
                btnDeact.setManaged(!"INACTIVE".equals(ft.getStatus()));
                setGraphic(box);
            }
        });

        tableView.setItems(data);
        tableView.setPlaceholder(new Label("No records found."));
    }

    // ════════════════════════════════════════════════════════════════════════
    //  DATA
    // ════════════════════════════════════════════════════════════════════════
    private void loadData() {
        try {
            data.setAll(service.getAll());
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Database Error", e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  SEARCH
    // ════════════════════════════════════════════════════════════════════════
    @FXML
    private void handleSearch() {
        try {
            data.setAll(service.search(txtSearch.getText()));
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Database Error", e.getMessage());
        }
    }

    @FXML
    private void handleClearSearch() {
        txtSearch.clear();
        loadData();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  SAVE (Add / Update)
    // ════════════════════════════════════════════════════════════════════════
    @FXML
    private void handleSave() {
        lblError.setText("");
        String typeName    = txtTypeName.getText();
        String description = txtDescription.getText();
        String status      = cmbStatus.getValue();

        try {
            String err;
            if (editingId == null) {
                err = service.add(typeName, description, status);
                if (err == null) showInfo("Furniture type added successfully!");
            } else {
                err = service.update(editingId, typeName, description, status);
                if (err == null) showInfo("Furniture type updated successfully!");
            }

            if (err != null) {
                lblError.setText("⚠ " + err);
                return;
            }
            handleClear();
            loadData();

        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Database Error", e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  DELETE (Hard Delete)
    // ════════════════════════════════════════════════════════════════════════
    @FXML
    private void handleDelete() {
        if (editingId == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection",
                      "Please select a furniture type to delete by clicking ✏ Edit first.");
            return;
        }

        // Find selected name for confirm dialog
        String name = data.stream()
                .filter(ft -> ft.getTypeId() == editingId)
                .map(FurnitureType::getTypeName)
                .findFirst()
                .orElse("ID " + editingId);

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Permanently delete \"" + name + "\"?\nThis action cannot be undone.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Delete");
        confirm.setHeaderText(null);

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.YES) return;

        try {
            String err = service.delete(editingId);
            if (err == null) {
                showInfo("Furniture type \"" + name + "\" deleted successfully.");
                handleClear();
                loadData();
            } else {
                showAlert(Alert.AlertType.WARNING, "Cannot Delete", err);
            }
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Database Error", e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  CLEAR FORM
    // ════════════════════════════════════════════════════════════════════════
    @FXML
    private void handleClear() {
        editingId = null;
        lblFormTitle.setText("➕  Add New Furniture Type");
        txtTypeName.clear();
        txtDescription.clear();
        cmbStatus.setValue("ACTIVE");
        lblError.setText("");
        btnSave.setText("Add");
        btnSave.setStyle("-fx-background-color:#27ae60;-fx-text-fill:white;" +
                         "-fx-font-weight:bold;-fx-cursor:hand;");
        btnDelete.setDisable(true); // disable Delete when no record selected
    }

    // ════════════════════════════════════════════════════════════════════════
    //  POPULATE FORM  (Edit mode)
    // ════════════════════════════════════════════════════════════════════════
    private void populateForm(FurnitureType ft) {
        editingId = ft.getTypeId();
        lblFormTitle.setText("✏  Edit Furniture Type  [ID: " + editingId + "]");
        txtTypeName.setText(ft.getTypeName());
        txtDescription.setText(ft.getDescription() != null ? ft.getDescription() : "");
        cmbStatus.setValue(ft.getStatus());
        lblError.setText("");
        btnSave.setText("Update");
        btnSave.setStyle("-fx-background-color:#e67e22;-fx-text-fill:white;" +
                         "-fx-font-weight:bold;-fx-cursor:hand;");
        btnDelete.setDisable(false); // enable Delete when a record is loaded
    }

    // ════════════════════════════════════════════════════════════════════════
    //  DEACTIVATE
    // ════════════════════════════════════════════════════════════════════════
    private void handleDeactivate(FurnitureType ft) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Deactivate \"" + ft.getTypeName() + "\"?\n"
              + "Its status will be set to INACTIVE.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Deactivation");
        confirm.setHeaderText(null);
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.YES) return;

        try {
            String err = service.deactivate(ft.getTypeId());
            if (err == null) {
                showInfo("Furniture type deactivated successfully.");
                loadData();
                handleClear();
            } else {
                showAlert(Alert.AlertType.WARNING, "Cannot Deactivate", err);
            }
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Database Error", e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  UTILITIES
    // ════════════════════════════════════════════════════════════════════════
    private void showInfo(String msg) {
        showAlert(Alert.AlertType.INFORMATION, "Success", msg);
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type, msg, ButtonType.OK);
        a.setTitle(title);
        a.setHeaderText(null);
        a.showAndWait();
    }
}