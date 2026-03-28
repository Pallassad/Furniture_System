package furniture_system.controller;

import furniture_system.model.*;
import furniture_system.service.OrderService;
import furniture_system.utils.NotificationUtil;
import furniture_system.utils.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Admin – Order Management & Billing
 * Fullwidth table + Modal Dialog pattern (same as AuthController).
 */
public class AdminOrderController {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ── Search bar ─────────────────────────────────────────────────────────
    @FXML private TextField txtSearch;

    // ── Order table ────────────────────────────────────────────────────────
    @FXML private TableView<Order>               tblOrders;
    @FXML private TableColumn<Order, Integer>    colOrderId;
    @FXML private TableColumn<Order, String>     colCustomer;
    @FXML private TableColumn<Order, String>     colEmployee;
    @FXML private TableColumn<Order, String>     colDate;
    @FXML private TableColumn<Order, String>     colStatus;
    @FXML private TableColumn<Order, BigDecimal> colFinal;

    // ── Toolbar buttons ────────────────────────────────────────────────────
    @FXML private Button btnViewDetail;
    @FXML private Button btnUpdateStatus;
    @FXML private Button btnManageBilling;
    @FXML private Button btnDeleteOrder;
    @FXML private Label  statusBarLabel;

    private final OrderService          orderService = new OrderService();
    private final ObservableList<Order> orderList    = FXCollections.observableArrayList();

    // ══════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        setupOrderTable();
        loadAllOrders();

        tblOrders.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            boolean has = sel != null;
            btnViewDetail.setDisable(!has);
            btnUpdateStatus.setDisable(!has);
            btnManageBilling.setDisable(!has);

            boolean canDelete = has && ("COMPLETED".equals(sel.getStatus())
                    || "CANCELLED".equals(sel.getStatus())
                    || "RETURNED".equals(sel.getStatus()));
            btnDeleteOrder.setDisable(!canDelete);
        });
        btnViewDetail.setDisable(true);
        btnUpdateStatus.setDisable(true);
        btnManageBilling.setDisable(true);
        btnDeleteOrder.setDisable(true);

        // Double-click → view detail
        tblOrders.setRowFactory(tv -> {
            TableRow<Order> row = new TableRow<>();
            row.setOnMouseClicked(e -> { if (e.getClickCount() == 2 && !row.isEmpty()) openDetailDialog(row.getItem()); });
            return row;
        });
    }

    // ── Table setup ────────────────────────────────────────────────────────
    private void setupOrderTable() {
        colOrderId.setCellValueFactory(new PropertyValueFactory<>("orderId"));
        colCustomer.setCellValueFactory(new PropertyValueFactory<>("customerName"));
        colEmployee.setCellValueFactory(new PropertyValueFactory<>("employeeName"));
        colDate.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getOrderDate() != null ? c.getValue().getOrderDate().format(DTF) : "—"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colFinal.setCellValueFactory(new PropertyValueFactory<>("finalTotal"));

        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); setStyle(""); return; }
                setText(s);
                setStyle(switch (s) {
                    case "COMPLETED"  -> "-fx-text-fill:#27ae60;-fx-font-weight:bold;";
                    case "CANCELLED"  -> "-fx-text-fill:#c62828;-fx-font-weight:bold;";
                    case "PAID"       -> "-fx-text-fill:#1565c0;-fx-font-weight:bold;";
                    case "DELIVERING" -> "-fx-text-fill:#e65100;-fx-font-weight:bold;";
                    case "CONFIRMED"  -> "-fx-text-fill:#6a1b9a;-fx-font-weight:bold;";
                    default           -> "-fx-text-fill:#555;";
                });
            }
        });

        colFinal.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : String.format("%,.0f ₫", v));
            }
        });

        tblOrders.setItems(orderList);
    }

    // ── Load & Search ──────────────────────────────────────────────────────
    private void loadAllOrders() {
        try {
            orderList.setAll(orderService.getAllOrders());
            setStatus("Loaded " + orderList.size() + " order(s).");
        } catch (Exception e) { alert(Alert.AlertType.ERROR, "Error", e.getMessage()); }
    }

    @FXML public void onRefresh() { clearSearch(); loadAllOrders(); }

    @FXML public void onSearch() {
        String kw = txtSearch == null ? "" : txtSearch.getText().trim().toLowerCase();
        if (kw.isBlank()) { loadAllOrders(); return; }

        // Try to parse as an integer for ID matching
        Integer idMatch = parseIntOrNull(kw);

        ObservableList<Order> filtered = FXCollections.observableArrayList(
            orderList.filtered(o ->
                (idMatch != null && (o.getOrderId() == idMatch ||
                                     o.getCustomerId() == idMatch ||
                                     o.getEmployeeId() == idMatch))  ||
                (o.getCustomerName() != null && o.getCustomerName().toLowerCase().contains(kw)) ||
                (o.getEmployeeName() != null && o.getEmployeeName().toLowerCase().contains(kw)) ||
                (o.getStatus()       != null && o.getStatus().toLowerCase().contains(kw))
            ));
        tblOrders.setItems(filtered);
        setStatus("Found " + filtered.size() + " order(s).");
    }

    @FXML public void onReset() { clearSearch(); loadAllOrders(); }

    private void clearSearch() {
        if (txtSearch != null) txtSearch.clear();
        tblOrders.setItems(orderList);
    }

    // ==================== VIEW DETAIL DIALOG ====================
    @FXML public void handleViewDetail() {
        Order sel = tblOrders.getSelectionModel().getSelectedItem();
        if (sel != null) openDetailDialog(sel);
    }

    private void openDetailDialog(Order order) {
        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Order Detail - #" + order.getOrderId());

        // Order lines table
        TableView<OrderLine> tblLines = new TableView<>();
        TableColumn<OrderLine, String>     cProd  = new TableColumn<>("Product");
        TableColumn<OrderLine, Integer>    cQty   = new TableColumn<>("Qty");
        TableColumn<OrderLine, BigDecimal> cPrice = new TableColumn<>("Unit Price");
        TableColumn<OrderLine, BigDecimal> cTotal = new TableColumn<>("Subtotal");
        cProd.setCellValueFactory(new PropertyValueFactory<>("productName")); cProd.setPrefWidth(220);
        cQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));     cQty.setPrefWidth(60);
        cPrice.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));  cPrice.setPrefWidth(130);
        cTotal.setCellValueFactory(new PropertyValueFactory<>("lineTotal"));  cTotal.setPrefWidth(130);
        formatMoneyCols(cPrice, cTotal);
        tblLines.getColumns().addAll(cProd, cQty, cPrice, cTotal);
        tblLines.setPrefHeight(200);

        try {
            tblLines.setItems(FXCollections.observableArrayList(
                    orderService.getLinesForOrder(order.getOrderId())));
        } catch (Exception e) { alert(Alert.AlertType.ERROR, "Error", e.getMessage()); return; }

        GridPane info = new GridPane();
        info.setHgap(16); info.setVgap(8);
        info.getColumnConstraints().addAll(new ColumnConstraints(130), new ColumnConstraints(280));

        int row = 0;
        Label lblTitle = new Label("Order #" + order.getOrderId());
        lblTitle.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#1a237e;");
        info.add(lblTitle,                      0, row, 2, 1); row++;
        info.add(new Separator(),               0, row, 2, 1); row++;
        info.add(sec("-- Order Info"),          0, row, 2, 1); row++;
        info.add(fl("Customer"),  0, row); info.add(val(nvl(order.getCustomerName())),  1, row++);
        info.add(fl("Employee"),  0, row); info.add(val(nvl(order.getEmployeeName())),  1, row++);
        info.add(fl("Date"),      0, row); info.add(val(order.getOrderDate() != null ? order.getOrderDate().format(DTF) : "—"), 1, row++);
        info.add(fl("Status"),    0, row); info.add(statusLabel(order.getStatus()),     1, row++);
        info.add(fl("Total"),     0, row);
        Label lblTotal = new Label(order.getFinalTotal() != null ? String.format("%,.0f ₫", order.getFinalTotal()) : "—");
        lblTotal.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#27ae60;");
        info.add(lblTotal, 1, row++);
        info.add(new Separator(),               0, row, 2, 1); row++;
        info.add(sec("-- Order Items"),         0, row, 2, 1); row++;
        info.add(tblLines,                      0, row, 2, 1); row++;
        info.setPadding(new Insets(24));

        Button btnClose = primaryBtn("Close");
        btnClose.setOnAction(ev -> dlg.close());
        HBox btns = new HBox(btnClose); btns.setAlignment(Pos.CENTER_RIGHT);
        btns.setPadding(new Insets(0, 24, 16, 24));

        VBox root = new VBox(new ScrollPane(info), btns);
        ((ScrollPane) root.getChildren().get(0)).setFitToWidth(true);
        dlg.setScene(new Scene(root, 580, 560)); dlg.showAndWait();
    }

    // ==================== UPDATE STATUS DIALOG ====================
    @FXML public void handleUpdateStatus() {
        Order sel = tblOrders.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Update Status - Order #" + sel.getOrderId());

        ComboBox<String> cbStatus = new ComboBox<>(FXCollections.observableArrayList(
                "CONFIRMED", "PAID", "DELIVERING", "COMPLETED", "CANCELLED", "RETURNED"));
        cbStatus.setMaxWidth(Double.MAX_VALUE);
        TextArea taNote = new TextArea(); taNote.setPrefRowCount(3); taNote.setWrapText(true);
        taNote.setPromptText("Note (required when adjusting PAID or later)…");

        Label lblCurrent = new Label("Current: " + sel.getStatus());
        lblCurrent.setStyle("-fx-text-fill:#555;-fx-font-size:12px;");
        Label lblErr = new Label();
        lblErr.setStyle("-fx-text-fill:#c62828;-fx-font-size:12px;"); lblErr.setWrapText(true);

        GridPane grid = buildGrid();
        int row = 0;
        grid.add(lblCurrent,                    0, row, 2, 1); row++;
        grid.add(new Separator(),               0, row, 2, 1); row++;
        grid.add(fl("New Status *"),   0, row); grid.add(cbStatus, 1, row++);
        grid.add(fl("Note"),           0, row); grid.add(taNote,   1, row++);
        grid.add(new Label(""),        0, row); grid.add(hint("Required when adjusting an order past PAID."), 1, row++);
        grid.add(new Separator(),               0, row, 2, 1); row++;
        grid.add(lblErr,                        0, row, 2, 1); row++;

        Button btnSave   = primaryBtn("Update Status");
        Button btnCancel = new Button("Cancel");
        btnCancel.setStyle("-fx-background-radius:6;-fx-padding:8 18;");
        HBox btns = new HBox(10, btnCancel, btnSave); btns.setAlignment(Pos.CENTER_RIGHT);
        grid.add(btns, 0, row, 2, 1);

        btnSave.setOnAction(ev -> {
            lblErr.setText("");
            String newStatus = cbStatus.getValue();
            if (newStatus == null) { lblErr.setText("Please select a new status."); return; }
            String note = taNote.getText().trim();
            if (isPastPaid(sel.getStatus()) && note.isBlank()) {
                lblErr.setText("A note is required when adjusting an order past PAID."); return;
            }
            try {
                orderService.updateStatus(sel.getOrderId(), newStatus, getActorId(), note);
                setStatus("Order #" + sel.getOrderId() + " → " + newStatus);
                NotificationUtil.success(tblOrders, "Order #" + sel.getOrderId() + " → " + newStatus);
                loadAllOrders(); dlg.close();
            } catch (Exception ex) { lblErr.setText(ex.getMessage()); }
        });
        btnCancel.setOnAction(ev -> dlg.close());

        dlg.setScene(new Scene(grid, 480, 320)); dlg.setResizable(false); dlg.showAndWait();
    }

    // ==================== BILLING DIALOG ====================
    @FXML public void handleManageBilling() {
        Order sel = tblOrders.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Invoice - Order #" + sel.getOrderId());

        ComboBox<String> cbPayMethod     = new ComboBox<>(FXCollections.observableArrayList("CASH","BANK_TRANSFER","CARD","OTHER"));
        ComboBox<String> cbBillingStatus = new ComboBox<>(FXCollections.observableArrayList("UNPAID","PARTIAL","PAID","REFUNDED","VOID"));
        TextField        tfAmount        = new TextField();
        TextArea         taNote          = new TextArea(); taNote.setPrefRowCount(3); taNote.setWrapText(true);
        cbPayMethod.setMaxWidth(Double.MAX_VALUE); cbBillingStatus.setMaxWidth(Double.MAX_VALUE);

        Label lblInfo = new Label();
        lblInfo.setStyle("-fx-font-size:12px;-fx-text-fill:#555;"); lblInfo.setWrapText(true);
        Label lblErr  = new Label();
        lblErr.setStyle("-fx-text-fill:#c62828;-fx-font-size:12px;"); lblErr.setWrapText(true);

        Billing[] existing = {null};
        try {
            existing[0] = orderService.getBillingByOrder(sel.getOrderId());
        } catch (Exception e) { alert(Alert.AlertType.ERROR, "Error", e.getMessage()); return; }

        String btnLabel;
        if (existing[0] != null) {
            lblInfo.setText("Invoice #" + existing[0].getInvoiceId()
                    + "  |  " + existing[0].getBillingStatus()
                    + "  |  Paid: " + String.format("%,.0f ₫", existing[0].getPaidAmount()));
            cbPayMethod.setValue(existing[0].getPaymentMethod());
            tfAmount.setText(existing[0].getPaidAmount().toPlainString());
            cbBillingStatus.setValue(existing[0].getBillingStatus());
            taNote.setText(existing[0].getNote() != null ? existing[0].getNote() : "");
            btnLabel = "Update Invoice";
        } else {
            lblInfo.setText("No invoice yet for Order #" + sel.getOrderId());
            cbPayMethod.setValue("CASH"); tfAmount.setText("0"); cbBillingStatus.setValue("UNPAID");
            btnLabel = "Create Invoice";
        }

        GridPane grid = buildGrid();
        int row = 0;
        grid.add(lblInfo,                           0, row, 2, 1); row++;
        grid.add(new Separator(),                   0, row, 2, 1); row++;
        grid.add(sec("-- Payment"),                 0, row, 2, 1); row++;
        grid.add(fl("Payment Method *"), 0, row); grid.add(cbPayMethod,     1, row++);
        grid.add(fl("Amount Paid *"),    0, row); grid.add(tfAmount,        1, row++);
        grid.add(fl("Invoice Status *"), 0, row); grid.add(cbBillingStatus, 1, row++);
        grid.add(fl("Note"),             0, row); grid.add(taNote,          1, row++);
        grid.add(new Separator(),                   0, row, 2, 1); row++;
        grid.add(lblErr,                            0, row, 2, 1); row++;

        Button btnSave   = primaryBtn(btnLabel);
        Button btnCancel = new Button("Cancel");
        btnCancel.setStyle("-fx-background-radius:6;-fx-padding:8 18;");
        HBox btns;

        // Delete invoice button — only shown when invoice exists and status is UNPAID/VOID
        if (existing[0] != null) {
            String bs = existing[0].getBillingStatus();
            boolean canDeleteBilling = "UNPAID".equals(bs) || "VOID".equals(bs);
            Button btnDelBilling = new Button("🗑 Delete Invoice");
            btnDelBilling.setStyle("-fx-background-color:#c62828;-fx-text-fill:white;" +
                "-fx-background-radius:6;-fx-padding:8 18;-fx-font-weight:bold;");
            btnDelBilling.setDisable(!canDeleteBilling);
            btnDelBilling.setOnAction(ev -> {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Delete invoice #" + existing[0].getInvoiceId() + "?\n" +
                    "You can create a new invoice later.",
                    ButtonType.YES, ButtonType.NO);
                confirm.setTitle("Confirm invoice deletion");
                confirm.setHeaderText(null);
                confirm.showAndWait().ifPresent(b -> {
                    if (b != ButtonType.YES) return;
                    try {
                        orderService.deleteBilling(existing[0].getInvoiceId());
                        setStatus("Deleted invoice #" + existing[0].getInvoiceId());
                        loadAllOrders();
                        dlg.close();
                    } catch (Exception ex) {
                        alert(Alert.AlertType.ERROR, "Cannot delete", ex.getMessage());
                    }
                });
            });
            btns = new HBox(10, btnDelBilling, btnCancel, btnSave);
        } else {
            btns = new HBox(10, btnCancel, btnSave);
        }

        btns.setAlignment(Pos.CENTER_RIGHT);
        grid.add(btns, 0, row, 2, 1);

        btnSave.setOnAction(ev -> {
            lblErr.setText("");
            try {
                BigDecimal amount = new BigDecimal(tfAmount.getText().trim());
                String note = taNote.getText().trim();
                if (existing[0] == null) {
                    Billing b = new Billing(sel.getOrderId(), cbPayMethod.getValue(),
                            amount, cbBillingStatus.getValue(), note.isBlank() ? null : note);
                    orderService.createBilling(b);
                } else {
                    existing[0].setPaymentMethod(cbPayMethod.getValue());
                    existing[0].setPaidAmount(amount);
                    existing[0].setBillingStatus(cbBillingStatus.getValue());
                    existing[0].setNote(note.isBlank() ? null : note);
                    orderService.updateBilling(existing[0], true);
                }
                setStatus("Invoice saved for Order #" + sel.getOrderId());
                NotificationUtil.success(tblOrders, "Invoice saved for Order #" + sel.getOrderId());
                loadAllOrders(); dlg.close();
            } catch (NumberFormatException ex) { lblErr.setText("Amount must be a valid number."); }
              catch (Exception ex) { lblErr.setText(ex.getMessage()); }
        });
        btnCancel.setOnAction(ev -> dlg.close());

        ScrollPane scroll = new ScrollPane(grid); scroll.setFitToWidth(true);
        dlg.setScene(new Scene(scroll, 480, 440)); dlg.showAndWait();
    }

    // ==================== DELETE ORDER ====================
    @FXML public void handleDeleteOrder() {
        Order sel = tblOrders.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Permanently delete order #" + sel.getOrderId()
            + " [" + sel.getCustomerName() + "]?\n\n"
            + "⚠ This action cannot be undone.\n"
            + "All order lines, invoices (if VOID) and\n"
            + "terminal warranty tickets will be deleted accordingly.",
            ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm order deletion");
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.YES) return;
            try {
                orderService.deleteOrder(sel.getOrderId());
                setStatus("Deleted order #" + sel.getOrderId() + ".");
                loadAllOrders();
            } catch (Exception ex) {
                alert(Alert.AlertType.ERROR, "Cannot delete", ex.getMessage());
            }
        });
    }

    // ==================== DELETE BILLING (in Manage Invoice dialog) ====================
    // This button is added dynamically in handleManageBilling when invoice exists with UNPAID/VOID status

    // ==================== CANCEL ORDER ====================
    @FXML public void handleCancelOrder() {
        Order sel = tblOrders.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Cancel Order #" + sel.getOrderId() + " [" + sel.getCustomerName() + "]?\n\nThis cannot be undone.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Cancellation"); confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.YES) return;
            try {
                orderService.cancelOrder(sel.getOrderId(), getActorId());
                setStatus("Order #" + sel.getOrderId() + " cancelled.");
                loadAllOrders();
            } catch (Exception ex) { alert(Alert.AlertType.ERROR, "Error", ex.getMessage()); }
        });
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    private void formatMoneyCols(TableColumn<OrderLine, BigDecimal>... cols) {
        for (TableColumn<OrderLine, BigDecimal> col : cols) {
            col.setCellFactory(c -> new TableCell<>() {
                @Override protected void updateItem(BigDecimal v, boolean empty) {
                    super.updateItem(v, empty);
                    setText(empty || v == null ? null : String.format("%,.0f ₫", v));
                }
            });
        }
    }

    private Label statusLabel(String s) {
        Label l = new Label(s != null ? s : "—");
        l.setStyle(switch (s != null ? s : "") {
            case "COMPLETED"  -> "-fx-text-fill:#27ae60;-fx-font-weight:bold;";
            case "CANCELLED"  -> "-fx-text-fill:#c62828;-fx-font-weight:bold;";
            case "PAID"       -> "-fx-text-fill:#1565c0;-fx-font-weight:bold;";
            case "DELIVERING" -> "-fx-text-fill:#e65100;-fx-font-weight:bold;";
            default           -> "-fx-text-fill:#555;";
        });
        return l;
    }

    private boolean isPastPaid(String status) {
        return switch (status != null ? status : "") {
            case "PAID", "DELIVERING", "COMPLETED", "RETURNED" -> true;
            default -> false;
        };
    }

    private int getActorId() {
        // StockLog.ActorId is a FK -> Employee.EmployeeId; do NOT use AccountId as a fallback.
        // The admin account must have a linked Employee row; otherwise an explicit error is thrown.
        Employee emp = SessionManager.getInstance().getCurrentEmployee();
        if (emp != null) return emp.getEmployeeId();
        throw new IllegalStateException(
            "No Employee record linked to this admin account.\n" +
            "Please ask the system admin to create an Employee row and link it to this account.");
    }

    private Integer parseIntOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    private GridPane buildGrid() {
        GridPane g = new GridPane(); g.setHgap(12); g.setVgap(10); g.setPadding(new Insets(24));
        g.getColumnConstraints().addAll(new ColumnConstraints(145), new ColumnConstraints(280));
        return g;
    }

    private Label fl(String t)   { Label l = new Label(t); l.setStyle("-fx-font-weight:bold;-fx-font-size:12px;"); return l; }
    private Label sec(String t)  { Label l = new Label(t); l.setStyle("-fx-font-weight:bold;-fx-font-size:11px;-fx-text-fill:#3949ab;"); return l; }
    private Label hint(String t) { Label l = new Label(t); l.setStyle("-fx-font-size:10px;-fx-text-fill:#888;"); return l; }
    private Label val(String t)  { Label l = new Label(t); l.setStyle("-fx-font-size:12px;"); l.setWrapText(true); return l; }
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
    private static String nvl(String s) { return s != null ? s : "—"; }
}