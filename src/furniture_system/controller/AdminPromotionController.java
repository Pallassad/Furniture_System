package furniture_system.controller;

import furniture_system.model.Promotion;
import furniture_system.service.PromotionService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Admin – Promotion Management (3.6.1 – 3.6.5)
 * Layout: full-width table, bottom panel form (toggle show/hide).
 */
public class AdminPromotionController implements Initializable {

    // ── Table ─────────────────────────────────────────────────────────────────

    @FXML private TableView<Promotion>               tableView;
    @FXML private TableColumn<Promotion, Integer>    colId;
    @FXML private TableColumn<Promotion, String>     colCode;
    @FXML private TableColumn<Promotion, String>     colName;
    @FXML private TableColumn<Promotion, String>     colType;
    @FXML private TableColumn<Promotion, String>     colValue;
    @FXML private TableColumn<Promotion, String>     colDates;
    @FXML private TableColumn<Promotion, String>     colUsage;
    @FXML private TableColumn<Promotion, String>     colStatus;
    @FXML private TableColumn<Promotion, Void>       colActions;

    // ── Search ────────────────────────────────────────────────────────────────

    @FXML private TextField        txtSearch;
    @FXML private ComboBox<String> cmbSearchStatus;
    @FXML private DatePicker       dpSearchFrom;
    @FXML private DatePicker       dpSearchTo;
    @FXML private Label            lblCount;
    @FXML private Label            lblMessage;

    // ── Form panel (bottom, toggle visible) ───────────────────────────────────

    @FXML private VBox             pnlForm;
    @FXML private Label            lblFormTitle;
    @FXML private TextField           txtCode;
    @FXML private TextField           txtName;
    @FXML private ComboBox<String>    cmbDiscountType;
    @FXML private TextField           txtDiscountValue;
    @FXML private DatePicker          dpStartDate;
    @FXML private DatePicker          dpEndDate;
    @FXML private TextField           txtMinOrderValue;
    @FXML private TextField           txtUsageLimit;
    @FXML private ComboBox<String>    cmbStatus;
    @FXML private Label               lblFormMessage;

    // ── Buttons ───────────────────────────────────────────────────────────────

    @FXML private Button btnAdd;
    @FXML private Button btnUpdate;
    @FXML private Button btnClear;

    // ── State ─────────────────────────────────────────────────────────────────

    private final PromotionService           svc  = new PromotionService();
    private final ObservableList<Promotion>  data = FXCollections.observableArrayList();
    private final DateTimeFormatter          dtf  = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private Promotion selectedPromotion = null;

    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initTable();
        initForm();
        refreshTable();
        // Form ẩn mặc định, chỉ hiện khi nhấn Add hoặc Edit
        pnlForm.setVisible(false);
        pnlForm.setManaged(false);
    }

    // ── Table setup ───────────────────────────────────────────────────────────

    private void initTable() {
        colId.setCellValueFactory(new PropertyValueFactory<>("promoId"));
        colCode.setCellValueFactory(new PropertyValueFactory<>("code"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));

        // "PERCENT 10 %" or "FIXED 200,000 ₫"
        colType.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().getDiscountType()));

        colValue.setCellValueFactory(cd -> {
            Promotion p = cd.getValue();
            String val = "PERCENT".equals(p.getDiscountType())
                    ? String.format("%.0f%%", p.getDiscountValue())
                    : String.format("%,.0f ₫", p.getDiscountValue());
            return new SimpleStringProperty(val);
        });

        colDates.setCellValueFactory(cd -> {
            Promotion p = cd.getValue();
            String s = p.getStartDate() != null ? p.getStartDate().format(dtf) : "?";
            String e = p.getEndDate()   != null ? p.getEndDate().format(dtf)   : "?";
            return new SimpleStringProperty(s + " → " + e);
        });

        colUsage.setCellValueFactory(cd -> {
            Promotion p = cd.getValue();
            String limit = p.getUsageLimit() != null ? String.valueOf(p.getUsageLimit()) : "∞";
            return new SimpleStringProperty(p.getUsedCount() + " / " + limit);
        });

        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        // Status badge colouring (same approach as AdminOrderController)
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty ? null : s);
                setStyle(empty || s == null ? "" : switch (s) {
                    case "ACTIVE"   -> "-fx-text-fill:#27ae60; -fx-font-weight:bold;";
                    case "UPCOMING" -> "-fx-text-fill:#1565c0; -fx-font-weight:bold;";
                    case "EXPIRED"  -> "-fx-text-fill:#e65100; -fx-font-weight:bold;";
                    case "DISABLED" -> "-fx-text-fill:#c62828; -fx-font-weight:bold;";
                    default         -> "";
                });
            }
        });

        // Actions column
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnEdit    = new Button("✏ Edit");
            private final Button btnDisable = new Button("🗑 Disable");
            {
                btnEdit.setStyle(
                    "-fx-background-color:#3949ab;-fx-text-fill:white;" +
                    "-fx-cursor:hand;-fx-background-radius:4;-fx-font-size:11;");
                btnDisable.setStyle(
                    "-fx-background-color:#c62828;-fx-text-fill:white;" +
                    "-fx-cursor:hand;-fx-background-radius:4;-fx-font-size:11;");

                btnEdit.setOnAction(e -> {
                    Promotion p = getTableRow().getItem();
                    if (p != null) populateForm(p);
                });
                btnDisable.setOnAction(e -> {
                    Promotion p = getTableRow().getItem();
                    if (p != null) confirmDisable(p);
                });
            }

            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                Promotion p = getTableRow().getItem();
                // Already disabled → hide Disable button
                btnDisable.setDisable("DISABLED".equals(p != null ? p.getStatus() : ""));
                setGraphic(new HBox(5, btnEdit, btnDisable));
            }
        });

        tableView.setItems(data);

        // Click row → populate form
        tableView.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, p) -> { if (p != null) populateForm(p); });
    }

    // ── Form / ComboBox setup ─────────────────────────────────────────────────

    private void initForm() {
        cmbDiscountType.setItems(FXCollections.observableArrayList("PERCENT", "FIXED"));
        cmbDiscountType.setValue("PERCENT");

        cmbStatus.setItems(FXCollections.observableArrayList(
                "UPCOMING", "ACTIVE", "EXPIRED", "DISABLED"));
        cmbStatus.setValue("UPCOMING");

        cmbSearchStatus.setItems(FXCollections.observableArrayList(
                "", "UPCOMING", "ACTIVE", "EXPIRED", "DISABLED"));

        btnUpdate.setDisable(true);
    }

    // ── Refresh & Search ──────────────────────────────────────────────────────

    private void refreshTable() {
        try {
            data.setAll(svc.getAll());
            updateCount();
            clearTableMsg();
        } catch (Exception e) {
            showTableError("Failed to load promotions: " + e.getMessage());
        }
    }

    @FXML private void handleSearch() {
        try {
            String keyword = txtSearch.getText();
            String status  = cmbSearchStatus.getValue();
            LocalDateTime from = dpSearchFrom.getValue() != null
                    ? dpSearchFrom.getValue().atStartOfDay() : null;
            LocalDateTime to   = dpSearchTo.getValue() != null
                    ? dpSearchTo.getValue().atTime(23, 59, 59) : null;

            data.setAll(svc.search(keyword, status, from, to));
            updateCount();
            clearTableMsg();
        } catch (Exception e) {
            showTableError("Search error: " + e.getMessage());
        }
    }

    @FXML private void handleClearSearch() {
        txtSearch.clear();
        cmbSearchStatus.setValue("");
        dpSearchFrom.setValue(null);
        dpSearchTo.setValue(null);
        refreshTable();
    }

    // ── Add ───────────────────────────────────────────────────────────────────

    @FXML private void handleAdd() {
        // Nếu form đang hiện cho Edit → reset về Add mode
        if (pnlForm.isVisible() && selectedPromotion != null) {
            clearFormFields();
            lblFormTitle.setText("✚  New Promotion");
        } else if (!pnlForm.isVisible()) {
            // Form đang ẩn → mở ra ở chế độ Add
            clearFormFields();
            lblFormTitle.setText("✚  New Promotion");
            showForm();
            return;
        }
        // Form đã mở ở chế độ Add → submit
        Promotion p = buildFromForm();
        if (p == null) return;
        try {
            svc.addPromotion(p);
            showFormSuccess("✔ Promotion added successfully.");
            refreshTable();
            hideForm();
        } catch (Exception ex) {
            showFormError(ex.getMessage());
        }
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @FXML private void handleUpdate() {
        if (selectedPromotion == null) {
            showFormError("Select a promotion to update.");
            return;
        }
        Promotion p = buildFromForm();
        if (p == null) return;
        p.setPromoId(selectedPromotion.getPromoId());
        p.setUsedCount(selectedPromotion.getUsedCount());
        try {
            svc.updatePromotion(p);
            showTableSuccess("✔ Promotion updated successfully.");
            refreshTable();
            hideForm();
        } catch (Exception ex) {
            showFormError(ex.getMessage());
        }
    }

    // ── Disable ───────────────────────────────────────────────────────────────

    private void confirmDisable(Promotion p) {
        Alert dlg = new Alert(Alert.AlertType.CONFIRMATION,
                "Disable promotion \"" + p.getCode() + "\"?\n\n"
                        + "It will no longer be applicable to new orders.",
                ButtonType.YES, ButtonType.NO);
        dlg.setTitle("Confirm Disable");
        dlg.setHeaderText(null);
        Optional<ButtonType> res = dlg.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.YES) {
            try {
                svc.disablePromotion(p.getPromoId());
                showTableSuccess("Promotion \"" + p.getCode() + "\" disabled.");
                refreshTable();
                clearForm();
            } catch (Exception ex) {
                showTableError("Error: " + ex.getMessage());
            }
        }
    }

    // ── Clear / Hide Form ─────────────────────────────────────────────────────

    @FXML private void handleClear() {
        hideForm();
    }

    private void showForm() {
        pnlForm.setVisible(true);
        pnlForm.setManaged(true);
    }

    private void hideForm() {
        pnlForm.setVisible(false);
        pnlForm.setManaged(false);
        clearFormFields();
        tableView.getSelectionModel().clearSelection();
    }

    private void clearForm() {
        hideForm();
    }

    private void clearFormFields() {
        selectedPromotion = null;
        txtCode.clear();
        txtName.clear();
        cmbDiscountType.setValue("PERCENT");
        txtDiscountValue.clear();
        dpStartDate.setValue(null);
        dpEndDate.setValue(null);
        txtMinOrderValue.clear();
        txtUsageLimit.clear();
        cmbStatus.setValue("UPCOMING");
        btnUpdate.setDisable(true);
        btnAdd.setDisable(false);
        clearFormMsg();
    }

    // ── Populate Form (Edit mode) ─────────────────────────────────────────────

    private void populateForm(Promotion p) {
        selectedPromotion = p;
        txtCode.setText(p.getCode());
        txtName.setText(p.getName());
        cmbDiscountType.setValue(p.getDiscountType());
        txtDiscountValue.setText(p.getDiscountValue() != null
                ? p.getDiscountValue().toPlainString() : "");
        dpStartDate.setValue(p.getStartDate() != null ? p.getStartDate().toLocalDate() : null);
        dpEndDate.setValue(p.getEndDate()     != null ? p.getEndDate().toLocalDate()   : null);
        txtMinOrderValue.setText(p.getMinOrderValue() != null
                ? p.getMinOrderValue().toPlainString() : "0");
        txtUsageLimit.setText(p.getUsageLimit() != null
                ? String.valueOf(p.getUsageLimit()) : "");
        cmbStatus.setValue(p.getStatus());
        btnUpdate.setDisable(false);
        btnAdd.setDisable(true);
        lblFormTitle.setText("✏  Edit Promotion – " + p.getCode());
        clearFormMsg();
        showForm();
    }

    // ── Build Promotion from Form ─────────────────────────────────────────────

    private Promotion buildFromForm() {
        // DiscountValue
        BigDecimal discountValue;
        try {
            discountValue = new BigDecimal(txtDiscountValue.getText().trim().replace(",", ""));
        } catch (NumberFormatException ex) {
            showFormError("Discount value must be a valid number.");
            return null;
        }

        // MinOrderValue
        BigDecimal minOrderValue;
        try {
            String raw = txtMinOrderValue.getText().trim().replace(",", "");
            minOrderValue = raw.isBlank() ? BigDecimal.ZERO : new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            showFormError("Minimum order value must be a valid number.");
            return null;
        }

        // UsageLimit (nullable)
        Integer usageLimit = null;
        String ulRaw = txtUsageLimit.getText().trim();
        if (!ulRaw.isBlank()) {
            try {
                usageLimit = Integer.parseInt(ulRaw);
            } catch (NumberFormatException ex) {
                showFormError("Usage limit must be a whole number (or leave blank for unlimited).");
                return null;
            }
        }

        // Dates
        if (dpStartDate.getValue() == null) { showFormError("Start date is required."); return null; }
        if (dpEndDate.getValue()   == null) { showFormError("End date is required.");   return null; }

        Promotion p = new Promotion();
        p.setCode(txtCode.getText().trim());
        p.setName(txtName.getText().trim());
        p.setDiscountType(cmbDiscountType.getValue());
        p.setDiscountValue(discountValue);
        p.setStartDate(dpStartDate.getValue().atStartOfDay());
        p.setEndDate(dpEndDate.getValue().atTime(23, 59, 59));
        p.setMinOrderValue(minOrderValue);
        p.setUsageLimit(usageLimit);
        p.setUsedCount(0);
        p.setStatus(cmbStatus.getValue());
        return p;
    }

    // ── UI Helpers ────────────────────────────────────────────────────────────

    private void updateCount() {
        lblCount.setText("Total: " + data.size() + " promotion(s)");
    }

    private void showTableError(String m) {
        lblMessage.setStyle("-fx-text-fill:#c62828; -fx-font-size:12;");
        lblMessage.setText("✘ " + m);
    }

    private void showTableSuccess(String m) {
        lblMessage.setStyle("-fx-text-fill:#27ae60; -fx-font-size:12;");
        lblMessage.setText(m);
    }

    private void clearTableMsg() {
        lblMessage.setText("");
        lblMessage.setStyle("");
    }

    private void showFormError(String m) {
        lblFormMessage.setStyle(
                "-fx-text-fill:#c62828; -fx-background-color:#ffebee;" +
                "-fx-padding:5 8; -fx-background-radius:4; -fx-font-size:12;");
        lblFormMessage.setText("✘ " + m);
    }

    private void showFormSuccess(String m) {
        lblFormMessage.setStyle(
                "-fx-text-fill:#27ae60; -fx-background-color:#e8f5e9;" +
                "-fx-padding:5 8; -fx-background-radius:4; -fx-font-size:12;");
        lblFormMessage.setText(m);
    }

    private void clearFormMsg() {
        lblFormMessage.setText("");
        lblFormMessage.setStyle("");
    }
}