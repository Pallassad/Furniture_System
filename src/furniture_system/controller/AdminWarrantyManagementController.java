package furniture_system.controller;

import furniture_system.dao.WarrantyTicketDAO;
import furniture_system.model.WarrantyTicket;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.math.BigDecimal;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Controller for Admin – Warranty & Repair Management.
 * Bound to admin_warranty_management.fxml
 */
public class AdminWarrantyManagementController implements Initializable {

    // ── Table ────────────────────────────────────────────────
    @FXML private TableView<WarrantyTicket> tableWarranty;
    @FXML private TableColumn<WarrantyTicket, String> colTicketId;
    @FXML private TableColumn<WarrantyTicket, String> colOrderId;
    @FXML private TableColumn<WarrantyTicket, String> colProduct;
    @FXML private TableColumn<WarrantyTicket, String> colCustomer;
    @FXML private TableColumn<WarrantyTicket, String> colHandler;
    @FXML private TableColumn<WarrantyTicket, String> colStatus;
    @FXML private TableColumn<WarrantyTicket, String> colCost;
    @FXML private TableColumn<WarrantyTicket, String> colCreatedAt;

    // ── Search ───────────────────────────────────────────────
    @FXML private TextField txtSearch;

    // ── Form fields ──────────────────────────────────────────
    @FXML private TextField    txtOrderId;
    @FXML private TextField    txtProductId;
    @FXML private TextField    txtCustomerId;
    @FXML private TextField    txtHandlerId;
    @FXML private TextArea     txtIssueDesc;
    @FXML private ComboBox<String> cmbStatus;
    @FXML private TextField    txtCost;
    @FXML private TextArea     txtNote;

    // ── Buttons ──────────────────────────────────────────────
    @FXML private Button btnAdd;
    @FXML private Button btnUpdate;
    @FXML private Button btnCancel;
    @FXML private Button btnClear;

    // ── Status bar ───────────────────────────────────────────
    @FXML private Label lblStatus;

    private final WarrantyTicketDAO dao = new WarrantyTicketDAO();
    private final ObservableList<WarrantyTicket> data = FXCollections.observableArrayList();

    private static final List<String> STATUSES = List.of(
            "CREATED", "RECEIVED", "PROCESSING", "WAITING_PART",
            "COMPLETED", "REJECTED", "CANCELLED");

    // ── Init ─────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupTable();
        cmbStatus.setItems(FXCollections.observableArrayList(STATUSES));
        cmbStatus.setValue("CREATED");

        tableWarranty.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> populateForm(sel));

        loadAll();
    }

    // ── Table setup ───────────────────────────────────────────

    private void setupTable() {
        colTicketId .setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getTicketId())));
        colOrderId  .setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getOrderId())));
        colProduct  .setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProductName()));
        colCustomer .setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCustomerName()));
        colHandler  .setCellValueFactory(c -> {
            String h = c.getValue().getHandlerName();
            return new SimpleStringProperty(h != null ? h : "—");
        });
        colStatus   .setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus()));
        colCost     .setCellValueFactory(c -> {
            BigDecimal cost = c.getValue().getCost();
            return new SimpleStringProperty(cost != null ? String.format("%,.0f ₫", cost) : "0 ₫");
        });
        colCreatedAt.setCellValueFactory(c -> {
            var dt = c.getValue().getCreatedAt();
            return new SimpleStringProperty(dt != null ? dt.toLocalDate().toString() : "");
        });

        // Row colour: CANCELLED / REJECTED → grey
        tableWarranty.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(WarrantyTicket item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) { getStyleClass().remove("row-inactive"); return; }
                if ("CANCELLED".equals(item.getStatus()) || "REJECTED".equals(item.getStatus()))
                    getStyleClass().add("row-inactive");
                else
                    getStyleClass().remove("row-inactive");
            }
        });

        tableWarranty.setItems(data);
        tableWarranty.setPlaceholder(new Label("No warranty tickets found."));
    }

    // ── Data loading ─────────────────────────────────────────

    private void loadAll() {
        try {
            data.setAll(dao.getAll());
            setStatus("Loaded " + data.size() + " ticket(s).", false);
        } catch (SQLException e) {
            showError("Load error", e.getMessage());
        }
    }

    // ── Search ───────────────────────────────────────────────

    @FXML
    private void handleSearch() {
        String kw = txtSearch.getText().trim();
        try {
            List<WarrantyTicket> result = kw.isEmpty() ? dao.getAll() : dao.search(kw);
            data.setAll(result);
            setStatus(result.size() + " result(s).", false);
        } catch (SQLException e) {
            showError("Search error", e.getMessage());
        }
    }

    // ── Add ──────────────────────────────────────────────────

    @FXML
    private void handleAdd() {
        WarrantyTicket t = readForm(null);
        if (t == null) return;
        try {
            int id = dao.insert(t);
            setStatus("Ticket #" + id + " created successfully.", false);
            loadAll();
            clearForm();
        } catch (SQLException e) {
            showError("Insert error", e.getMessage());
        }
    }

    // ── Update ───────────────────────────────────────────────

    @FXML
    private void handleUpdate() {
        WarrantyTicket selected = tableWarranty.getSelectionModel().getSelectedItem();
        if (selected == null) { showError("No selection", "Please select a ticket to update."); return; }

        WarrantyTicket t = readForm(selected.getTicketId());
        if (t == null) return;

        try {
            dao.update(t);
            setStatus("Ticket #" + selected.getTicketId() + " updated.", false);
            loadAll();
            clearForm();
        } catch (SQLException e) {
            showError("Update error", e.getMessage());
        }
    }

    // ── Cancel / soft-delete ──────────────────────────────────

    @FXML
    private void handleCancel() {
        WarrantyTicket selected = tableWarranty.getSelectionModel().getSelectedItem();
        if (selected == null) { showError("No selection", "Please select a ticket to cancel."); return; }

        // Let admin choose CANCELLED or REJECTED
        ChoiceDialog<String> dlg = new ChoiceDialog<>("CANCELLED", "CANCELLED", "REJECTED");
        dlg.setTitle("Cancel / Reject Ticket");
        dlg.setHeaderText("Ticket #" + selected.getTicketId());
        dlg.setContentText("Set status to:");
        Optional<String> choice = dlg.showAndWait();
        if (choice.isEmpty()) return;

        try {
            dao.cancel(selected.getTicketId(), choice.get());
            setStatus("Ticket #" + selected.getTicketId() + " → " + choice.get(), false);
            loadAll();
            clearForm();
        } catch (Exception e) {
            showError("Cancel error", e.getMessage());
        }
    }

    // ── Clear form ────────────────────────────────────────────

    @FXML
    private void handleClear() { clearForm(); }

    // ── Helpers ───────────────────────────────────────────────

    private void populateForm(WarrantyTicket t) {
        if (t == null) return;
        txtOrderId  .setText(String.valueOf(t.getOrderId()));
        txtProductId.setText(String.valueOf(t.getProductId()));
        txtCustomerId.setText(String.valueOf(t.getCustomerId()));
        txtHandlerId.setText(t.getHandlerEmployeeId() != null ? String.valueOf(t.getHandlerEmployeeId()) : "");
        txtIssueDesc.setText(t.getIssueDesc());
        cmbStatus   .setValue(t.getStatus());
        txtCost     .setText(t.getCost() != null ? t.getCost().toPlainString() : "0");
        txtNote     .setText(t.getNote() != null ? t.getNote() : "");
    }

    private void clearForm() {
        txtOrderId.clear(); txtProductId.clear(); txtCustomerId.clear();
        txtHandlerId.clear(); txtIssueDesc.clear(); txtNote.clear();
        txtCost.setText("0");
        cmbStatus.setValue("CREATED");
        tableWarranty.getSelectionModel().clearSelection();
        setStatus("Ready.", false);
    }

    /**
     * Reads and validates form fields.
     * @param ticketId pass existing ID for update, null for insert
     */
    private WarrantyTicket readForm(Integer ticketId) {
        try {
            int orderId    = Integer.parseInt(txtOrderId.getText().trim());
            int productId  = Integer.parseInt(txtProductId.getText().trim());
            int customerId = Integer.parseInt(txtCustomerId.getText().trim());

            String handlerStr = txtHandlerId.getText().trim();
            Integer handlerId = handlerStr.isEmpty() ? null : Integer.parseInt(handlerStr);

            String issueDesc = txtIssueDesc.getText().trim();
            if (issueDesc.isEmpty()) { showError("Validation", "Issue Description is required."); return null; }

            String status = cmbStatus.getValue();
            if (status == null) { showError("Validation", "Status is required."); return null; }

            BigDecimal cost = new BigDecimal(txtCost.getText().trim().isEmpty() ? "0" : txtCost.getText().trim());
            if (cost.compareTo(BigDecimal.ZERO) < 0) { showError("Validation", "Cost must be ≥ 0."); return null; }

            String note = txtNote.getText().trim();

            WarrantyTicket t = new WarrantyTicket();
            if (ticketId != null) t.setTicketId(ticketId);
            t.setOrderId(orderId);
            t.setProductId(productId);
            t.setCustomerId(customerId);
            t.setHandlerEmployeeId(handlerId);
            t.setIssueDesc(issueDesc);
            t.setStatus(status);
            t.setCost(cost);
            t.setNote(note.isEmpty() ? null : note);
            return t;

        } catch (NumberFormatException e) {
            showError("Validation", "Please enter valid numeric IDs and cost.");
            return null;
        }
    }

    private void setStatus(String msg, boolean isError) {
        if (lblStatus != null) {
            lblStatus.setText(msg);
            lblStatus.setStyle(isError ? "-fx-text-fill:#c62828;" : "-fx-text-fill:#37474f;");
        }
    }

    private void showError(String title, String msg) {
        setStatus("⚠ " + msg, true);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
