package furniture_system.controller;

import furniture_system.model.*;
import furniture_system.service.OrderService;
import furniture_system.utils.SessionManager;
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
import java.util.List;
import java.util.ResourceBundle;

/**
 * Admin – Order Management & Billing (3.11.1 – 3.11.6)
 */
public class AdminOrderController implements Initializable {

    // ── FXML injections ──────────────────────────────────────────────────

    // Search bar
    @FXML private TextField        tfSearchOrderId;
    @FXML private TextField        tfSearchCustomerId;
    @FXML private TextField        tfSearchEmployeeId;
    @FXML private ComboBox<String> cbSearchStatus;
    @FXML private DatePicker       dpFrom;
    @FXML private DatePicker       dpTo;
    @FXML private Button           btnSearch;
    @FXML private Button           btnReset;

    // Order table
    @FXML private TableView<Order>               tblOrders;
    @FXML private TableColumn<Order, Integer>    colOrderId;
    @FXML private TableColumn<Order, String>     colCustomer;
    @FXML private TableColumn<Order, String>     colEmployee;
    @FXML private TableColumn<Order, String>     colDate;
    @FXML private TableColumn<Order, String>     colStatus;
    @FXML private TableColumn<Order, BigDecimal> colFinal;
    @FXML private TableColumn<Order, Void>       colActions;

    // Order Lines detail panel
    @FXML private VBox   pnlOrderDetail;
    @FXML private Label  lblDetailOrderId;
    @FXML private TableView<OrderLine>               tblLines;
    @FXML private TableColumn<OrderLine, String>     colProduct;
    @FXML private TableColumn<OrderLine, Integer>    colQty;
    @FXML private TableColumn<OrderLine, BigDecimal> colUnitPrice;
    @FXML private TableColumn<OrderLine, BigDecimal> colLineTotal;

    // Billing panel
    @FXML private VBox             pnlBilling;
    @FXML private Label            lblBillingStatus;
    @FXML private ComboBox<String> cbPayMethod;
    @FXML private TextField        tfPaidAmount;
    @FXML private ComboBox<String> cbBillingStatus;
    @FXML private TextArea         taBillingNote;
    @FXML private Button           btnSaveBilling;

    // Status update
    @FXML private ComboBox<String> cbNewStatus;
    @FXML private TextArea         taStatusNote;
    @FXML private Button           btnUpdateStatus;
    @FXML private Button           btnCancelOrder;

    // Change 1: New status bar label field
    @FXML private Label statusBarLabel;

    // ── Fields ───────────────────────────────────────────────────────────

    private final OrderService             orderService = new OrderService();
    private final ObservableList<Order>    orderList    = FXCollections.observableArrayList();
    private final DateTimeFormatter        dtf          = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private Order selectedOrder;

    // ─────────────────────────────────────────────────────────────────────
    //  INIT
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupOrderTable();
        setupLineTable();
        setupComboBoxes();
        loadAllOrders();

        // Change 2: Hide detail panel and remove it from layout flow on startup
        pnlOrderDetail.setVisible(false);
        pnlOrderDetail.setManaged(false);

        pnlBilling.setVisible(false);

        tblOrders.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, sel) -> onOrderSelected(sel));
    }

    // ─────────────────────────────────────────────────────────────────────
    //  TABLE SETUP
    // ─────────────────────────────────────────────────────────────────────

    private void setupOrderTable() {
        colOrderId.setCellValueFactory(new PropertyValueFactory<>("orderId"));
        colCustomer.setCellValueFactory(new PropertyValueFactory<>("customerName"));
        colEmployee.setCellValueFactory(new PropertyValueFactory<>("employeeName"));
        colDate.setCellValueFactory(cd ->
                new SimpleStringProperty(
                        cd.getValue().getOrderDate() != null
                                ? cd.getValue().getOrderDate().format(dtf) : ""));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colFinal.setCellValueFactory(new PropertyValueFactory<>("finalTotal"));

        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty ? null : s);
                if (!empty)
                    getStyleClass().setAll("status-badge",
                            "status-" + (s != null ? s.toLowerCase() : ""));
            }
        });

        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnView   = new Button("View");
            private final Button btnCancel = new Button("Cancel");
            {   btnView.getStyleClass().add("btn-primary-sm");
                btnCancel.getStyleClass().add("btn-danger-sm");
                btnView.setOnAction(e ->
                        onViewDetail(getTableView().getItems().get(getIndex())));
                btnCancel.setOnAction(e ->
                        onCancelOrder(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                Order o = getTableView().getItems().get(getIndex());
                btnCancel.setDisable(
                        !"DRAFT".equals(o.getStatus()) && !"CONFIRMED".equals(o.getStatus()));
                setGraphic(new HBox(6, btnView, btnCancel));
            }
        });

        tblOrders.setItems(orderList);
    }

    private void setupLineTable() {
        colProduct.setCellValueFactory(new PropertyValueFactory<>("productName"));
        colQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colUnitPrice.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));
        colLineTotal.setCellValueFactory(new PropertyValueFactory<>("lineTotal"));
    }

    private void setupComboBoxes() {
        cbSearchStatus.setItems(FXCollections.observableArrayList(
                "", "DRAFT","CONFIRMED","PAID","DELIVERING","COMPLETED","CANCELLED","RETURNED"));
        cbNewStatus.setItems(FXCollections.observableArrayList(
                "CONFIRMED","PAID","DELIVERING","COMPLETED","CANCELLED","RETURNED"));
        cbPayMethod.setItems(FXCollections.observableArrayList(
                "CASH","BANK_TRANSFER","CARD","OTHER"));
        cbBillingStatus.setItems(FXCollections.observableArrayList(
                "UNPAID","PARTIAL","PAID","REFUNDED","VOID"));
    }

    // ─────────────────────────────────────────────────────────────────────
    //  LOAD DATA
    // ─────────────────────────────────────────────────────────────────────

    private void loadAllOrders() {
        try {
            orderList.setAll(orderService.getAllOrders());
        } catch (Exception e) {
            showError("Error loading orders", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  SEARCH  (3.11.6)
    // ─────────────────────────────────────────────────────────────────────

    @FXML
    private void onSearch() {
        try {
            Integer orderId    = parseIntOrNull(tfSearchOrderId.getText());
            Integer customerId = parseIntOrNull(tfSearchCustomerId.getText());
            Integer employeeId = parseIntOrNull(tfSearchEmployeeId.getText());
            String  status     = cbSearchStatus.getValue();
            LocalDateTime from = dpFrom.getValue() != null ? dpFrom.getValue().atStartOfDay()   : null;
            LocalDateTime to   = dpTo.getValue()   != null ? dpTo.getValue().atTime(23,59,59)   : null;

            List<Order> result = orderService.searchOrders(customerId, employeeId, orderId,
                    (status != null && !status.isBlank()) ? status : null, from, to);
            orderList.setAll(result);
        } catch (Exception e) {
            showError("Search error", e.getMessage());
        }
    }

    @FXML
    private void onReset() {
        tfSearchOrderId.clear();
        tfSearchCustomerId.clear();
        tfSearchEmployeeId.clear();
        cbSearchStatus.setValue(null);
        dpFrom.setValue(null);
        dpTo.setValue(null);
        loadAllOrders();
    }

    // ─────────────────────────────────────────────────────────────────────
    //  ORDER SELECTION
    // ─────────────────────────────────────────────────────────────────────

    private void onOrderSelected(Order order) {
        // Change 3: Also toggle managed so the panel truly collapses when hidden
        if (order == null) {
            pnlOrderDetail.setVisible(false);
            pnlOrderDetail.setManaged(false);
            return;
        }
        selectedOrder = order;
        loadOrderDetail(order);
        pnlOrderDetail.setManaged(true); // Restore layout space before making visible
    }

    private void onViewDetail(Order order) {
        tblOrders.getSelectionModel().select(order);
    }

    private void loadOrderDetail(Order order) {
        try {
            lblDetailOrderId.setText("Order #" + order.getOrderId()
                    + " – " + order.getCustomerName()
                    + " [" + order.getStatus() + "]");

            List<OrderLine> lines = orderService.getLinesForOrder(order.getOrderId());
            tblLines.setItems(FXCollections.observableArrayList(lines));
            pnlOrderDetail.setVisible(true);

            loadBillingPanel(order);
            cbNewStatus.setValue(null);
            taStatusNote.clear();
        } catch (Exception e) {
            showError("Error loading order detail", e.getMessage());
        }
    }

    private void loadBillingPanel(Order order) {
        try {
            Billing billing = orderService.getBillingByOrder(order.getOrderId());
            if (billing != null) {
                lblBillingStatus.setText("Invoice #" + billing.getInvoiceId()
                        + " | " + billing.getBillingStatus()
                        + " | Paid: " + billing.getPaidAmount());
                cbPayMethod.setValue(billing.getPaymentMethod());
                tfPaidAmount.setText(billing.getPaidAmount().toPlainString());
                cbBillingStatus.setValue(billing.getBillingStatus());
                taBillingNote.setText(billing.getNote());
                btnSaveBilling.setText("Update Invoice");
            } else {
                lblBillingStatus.setText("No invoice yet");
                cbPayMethod.setValue("CASH");
                tfPaidAmount.setText("0");
                cbBillingStatus.setValue("UNPAID");
                taBillingNote.clear();
                btnSaveBilling.setText("Create Invoice");
            }
            pnlBilling.setVisible(true);
        } catch (Exception e) {
            showError("Error loading billing", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  UPDATE STATUS  (3.11.3)
    // ─────────────────────────────────────────────────────────────────────

    @FXML
    private void onUpdateStatus() {
        if (selectedOrder == null) return;
        String newStatus = cbNewStatus.getValue();
        if (newStatus == null || newStatus.isBlank()) {
            showWarning("Please select a new status.");
            return;
        }

        String note = taStatusNote.getText().trim();
        if (isPastPaid(selectedOrder.getStatus()) && note.isBlank()) {
            showWarning("A note is required when adjusting an order that has already been paid (PAID).");
            return;
        }

        try {
            int actorId = getActorId();
            orderService.updateStatus(selectedOrder.getOrderId(), newStatus, actorId, note);
            showInfo("Order #" + selectedOrder.getOrderId()
                    + " status updated \u2192 " + newStatus);
            loadAllOrders();
            loadOrderDetail(orderService.getOrderById(selectedOrder.getOrderId()));
        } catch (Exception e) {
            showError("Error updating status", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  CANCEL ORDER  (3.11.4)
    // ─────────────────────────────────────────────────────────────────────

    private void onCancelOrder(Order order) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Cancel order #" + order.getOrderId() + "?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm cancellation");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                try {
                    int actorId = getActorId();
                    orderService.cancelOrder(order.getOrderId(), actorId);
                    showInfo("Order #" + order.getOrderId() + " has been cancelled.");
                    loadAllOrders();

                    // Change 4: Also clear managed so the panel fully collapses after cancel
                    pnlOrderDetail.setVisible(false);
                    pnlOrderDetail.setManaged(false);
                } catch (Exception e) {
                    showError("Error cancelling order", e.getMessage());
                }
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    //  BILLING  (3.11.5)
    // ─────────────────────────────────────────────────────────────────────

    @FXML
    private void onSaveBilling() {
        if (selectedOrder == null) return;
        try {
            BigDecimal paidAmount    = new BigDecimal(tfPaidAmount.getText().trim());
            String     payMethod     = cbPayMethod.getValue();
            String     billingStatus = cbBillingStatus.getValue();
            String     note          = taBillingNote.getText().trim();

            Billing existing = orderService.getBillingByOrder(selectedOrder.getOrderId());
            if (existing == null) {
                Billing b = new Billing(selectedOrder.getOrderId(), payMethod,
                        paidAmount, billingStatus, note.isBlank() ? null : note);
                orderService.createBilling(b);
                showInfo("Invoice created successfully.");
            } else {
                existing.setPaymentMethod(payMethod);
                existing.setPaidAmount(paidAmount);
                existing.setBillingStatus(billingStatus);
                existing.setNote(note.isBlank() ? null : note);
                orderService.updateBilling(existing, true); // isAdmin = true
                showInfo("Invoice updated successfully.");
            }
            loadBillingPanel(selectedOrder);
        } catch (NumberFormatException e) {
            showWarning("Invalid amount.");
        } catch (Exception e) {
            showError("Error saving invoice", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  UTILITY
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Returns the actor ID to record in the OrderLog.
     *
     * Logic:
     *  - Admin login (no currentEmployee) → use AccountId as actorId
     *  - Employee login                   → use EmployeeId
     *
     * Reason: Admin login only stores an Account in the session (no Employee
     * record), so getCurrentEmployee().getEmployeeId() cannot be called.
     * AccountId is still a valid ID for logging who performed the action.
     */
    private int getActorId() {
        SessionManager session = SessionManager.getInstance();

        // Prefer Employee (employee login)
        Employee emp = session.getCurrentEmployee();
        if (emp != null) return emp.getEmployeeId();

        // Fallback: Admin login → use AccountId
        Account account = session.getCurrentAccount();
        if (account != null) return account.getAccountId();

        throw new IllegalStateException("Invalid session.");
    }

    private boolean isPastPaid(String status) {
        return switch (status) {
            case "PAID", "DELIVERING", "COMPLETED", "RETURNED" -> true;
            default -> false;
        };
    }

    private Integer parseIntOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return null; }
    }

    private void showError(String header, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setHeaderText(header); a.setContentText(msg); a.showAndWait();
    }
    private void showWarning(String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }
    private void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }
}