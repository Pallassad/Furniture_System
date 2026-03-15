package furniture_system.controller;

import furniture_system.dao.WarrantyTicketDAO;
import furniture_system.model.WarrantyTicket;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;

import java.math.BigDecimal;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller for Employee – Warranty / Repair Management.
 * Bound to emp_warranty_management.fxml
 *
 * The current employee's EmployeeId MUST be injected via
 * {@link #setCurrentEmployeeId(int)} before the view is shown.
 */
public class EmpWarrantyController implements Initializable {

    // ── Table ────────────────────────────────────────────────
    @FXML private TableView<WarrantyTicket> tableWarranty;
    @FXML private TableColumn<WarrantyTicket, String> colTicketId;
    @FXML private TableColumn<WarrantyTicket, String> colOrderId;
    @FXML private TableColumn<WarrantyTicket, String> colProduct;
    @FXML private TableColumn<WarrantyTicket, String> colCustomer;
    @FXML private TableColumn<WarrantyTicket, String> colStatus;
    @FXML private TableColumn<WarrantyTicket, String> colCost;
    @FXML private TableColumn<WarrantyTicket, String> colCreatedAt;

    // ── Create section ───────────────────────────────────────
    @FXML private TextField  txtNewOrderId;
    @FXML private TextField  txtNewProductId;
    @FXML private TextField  txtNewCustomerId;
    @FXML private TextArea   txtNewIssueDesc;
    @FXML private TextArea   txtNewNote;
    @FXML private Button     btnCreate;

    // ── Update section ───────────────────────────────────────
    @FXML private TextField        txtUpdTicketId;
    @FXML private ComboBox<String> cmbUpdStatus;
    @FXML private TextField        txtUpdCost;
    @FXML private TextArea         txtUpdNote;
    @FXML private Button           btnUpdateTicket;

    // ── Status bar ───────────────────────────────────────────
    @FXML private Label lblEmpStatus;

    private final WarrantyTicketDAO dao = new WarrantyTicketDAO();
    private final ObservableList<WarrantyTicket> data = FXCollections.observableArrayList();
    private int currentEmployeeId;

    private static final List<String> UPDATE_STATUSES = List.of(
            "RECEIVED", "PROCESSING", "WAITING_PART", "COMPLETED", "REJECTED", "CANCELLED");

    // ── Dependency injection ──────────────────────────────────

    public void setCurrentEmployeeId(int id) {
        this.currentEmployeeId = id;
        loadAssigned();
    }

    // ── Init ─────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupTable();
        cmbUpdStatus.setItems(FXCollections.observableArrayList(UPDATE_STATUSES));

        // Auto-fill TicketId on table selection
        tableWarranty.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> {
                    if (sel != null) {
                        txtUpdTicketId.setText(String.valueOf(sel.getTicketId()));
                        cmbUpdStatus.setValue(sel.getStatus());
                        txtUpdCost.setText(sel.getCost() != null ? sel.getCost().toPlainString() : "0");
                        txtUpdNote.setText(sel.getNote() != null ? sel.getNote() : "");
                    }
                });
    }

    // ── Table setup ───────────────────────────────────────────

    private void setupTable() {
        colTicketId .setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getTicketId())));
        colOrderId  .setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getOrderId())));
        colProduct  .setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProductName()));
        colCustomer .setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCustomerName()));
        colStatus   .setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus()));
        colCost     .setCellValueFactory(c -> {
            BigDecimal cost = c.getValue().getCost();
            return new SimpleStringProperty(cost != null ? String.format("%,.0f ₫", cost) : "0 ₫");
        });
        colCreatedAt.setCellValueFactory(c -> {
            var dt = c.getValue().getCreatedAt();
            return new SimpleStringProperty(dt != null ? dt.toLocalDate().toString() : "");
        });

        tableWarranty.setItems(data);
        tableWarranty.setPlaceholder(new Label("No assigned tickets found."));
    }

    // ── Load assigned tickets ─────────────────────────────────

    private void loadAssigned() {
        try {
            data.setAll(dao.getByHandler(currentEmployeeId));
            setStatus(data.size() + " assigned ticket(s).", false);
        } catch (SQLException e) {
            showError("Load error", e.getMessage());
        }
    }

    // ── Create new ticket ─────────────────────────────────────

    @FXML
    private void handleCreate() {
        try {
            int orderId    = Integer.parseInt(txtNewOrderId.getText().trim());
            int productId  = Integer.parseInt(txtNewProductId.getText().trim());
            int customerId = Integer.parseInt(txtNewCustomerId.getText().trim());
            String issueDesc = txtNewIssueDesc.getText().trim();
            if (issueDesc.isEmpty()) { showError("Validation", "Issue Description is required."); return; }
            String note = txtNewNote.getText().trim();

            WarrantyTicket t = new WarrantyTicket();
            t.setOrderId(orderId);
            t.setProductId(productId);
            t.setCustomerId(customerId);
            t.setHandlerEmployeeId(currentEmployeeId);  // auto-assign to self
            t.setIssueDesc(issueDesc);
            t.setStatus("CREATED");
            t.setCost(BigDecimal.ZERO);
            t.setNote(note.isEmpty() ? null : note);

            int id = dao.insert(t);
            setStatus("Ticket #" + id + " created.", false);

            // Clear create-form
            txtNewOrderId.clear(); txtNewProductId.clear();
            txtNewCustomerId.clear(); txtNewIssueDesc.clear(); txtNewNote.clear();

            loadAssigned();

        } catch (NumberFormatException e) {
            showError("Validation", "Please enter valid numeric IDs.");
        } catch (SQLException e) {
            showError("Create error", e.getMessage());
        }
    }

    // ── Update status / cost / note ───────────────────────────

    @FXML
    private void handleUpdateTicket() {
        String tidStr = txtUpdTicketId.getText().trim();
        if (tidStr.isEmpty()) { showError("Validation", "Ticket ID is required."); return; }

        try {
            int ticketId = Integer.parseInt(tidStr);
            String status = cmbUpdStatus.getValue();
            if (status == null) { showError("Validation", "Status is required."); return; }

            BigDecimal cost = new BigDecimal(txtUpdCost.getText().trim().isEmpty() ? "0" : txtUpdCost.getText().trim());
            if (cost.compareTo(BigDecimal.ZERO) < 0) { showError("Validation", "Cost must be ≥ 0."); return; }

            String note = txtUpdNote.getText().trim();

            dao.updateStatusCostNote(ticketId, status, cost, note.isEmpty() ? null : note);
            setStatus("Ticket #" + ticketId + " updated → " + status, false);

            loadAssigned();
            clearUpdateForm();

        } catch (NumberFormatException e) {
            showError("Validation", "Please enter a valid Ticket ID and numeric cost.");
        } catch (SQLException e) {
            showError("Update error", e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    @FXML
    private void handleRefresh() { loadAssigned(); }

    private void clearUpdateForm() {
        txtUpdTicketId.clear(); txtUpdCost.clear(); txtUpdNote.clear();
        cmbUpdStatus.setValue(null);
        tableWarranty.getSelectionModel().clearSelection();
    }

    private void setStatus(String msg, boolean isError) {
        if (lblEmpStatus != null) {
            lblEmpStatus.setText(msg);
            lblEmpStatus.setStyle(isError ? "-fx-text-fill:#c62828;" : "-fx-text-fill:#37474f;");
        }
    }

    private void showError(String title, String msg) {
        setStatus("⚠ " + msg, true);
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(msg);
        a.showAndWait();
    }
}
