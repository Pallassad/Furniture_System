package furniture_system.controller;

import furniture_system.dao.CustomerDAO;
import furniture_system.dao.DeliveryAddressDAO;
import furniture_system.dao.ProductDAO;
import furniture_system.dao.PromotionDAO;
import furniture_system.model.*;
import furniture_system.service.OrderService;
import furniture_system.utils.NotificationUtil;
import furniture_system.utils.SearchableComboBox;
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
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Employee – Order Management & Billing
 * Fullwidth table + Modal Dialog pattern (same as AuthController).
 */
public class EmployeeOrderController {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ── Order table ────────────────────────────────────────────────────────
    @FXML private TableView<Order>               tblMyOrders;
    @FXML private TableColumn<Order, Integer>    colOrderId;
    @FXML private TableColumn<Order, String>     colCustomer;
    @FXML private TableColumn<Order, String>     colDate;
    @FXML private TableColumn<Order, String>     colStatus;
    @FXML private TableColumn<Order, BigDecimal> colFinal;

    // ── Search & Toolbar ───────────────────────────────────────────────────
    @FXML private TextField txtSearch;
    @FXML private Button    btnNewOrder;
    @FXML private Button    btnViewDetail;
    @FXML private Button    btnUpdateStatus;
    @FXML private Button    btnManageBilling;
    @FXML private Label     statusBarLabel;

    private final OrderService       orderService = new OrderService();
    private final CustomerDAO        customerDAO  = new CustomerDAO();
    private final DeliveryAddressDAO addressDAO   = new DeliveryAddressDAO();
    private final ProductDAO         productDAO   = new ProductDAO();
    private final PromotionDAO       promoDAO     = new PromotionDAO();

    private final ObservableList<Order> myOrders = FXCollections.observableArrayList();

    // ══════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        setupMyOrdersTable();
        loadMyOrders();

        tblMyOrders.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            boolean has = sel != null;
            btnViewDetail.setDisable(!has);
            btnUpdateStatus.setDisable(!has);
            btnManageBilling.setDisable(!has);
        });
        btnViewDetail.setDisable(true);
        btnUpdateStatus.setDisable(true);
        btnManageBilling.setDisable(true);

        // Double-click → view detail
        tblMyOrders.setRowFactory(tv -> {
            TableRow<Order> row = new TableRow<>();
            row.setOnMouseClicked(e -> { if (e.getClickCount() == 2 && !row.isEmpty()) openDetailDialog(row.getItem()); });
            return row;
        });
    }

    // ── Table setup ────────────────────────────────────────────────────────
    private void setupMyOrdersTable() {
        colOrderId.setCellValueFactory(new PropertyValueFactory<>("orderId"));
        colCustomer.setCellValueFactory(new PropertyValueFactory<>("customerName"));
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

        tblMyOrders.setItems(myOrders);
    }

    // ── Load & Search ──────────────────────────────────────────────────────
    private void loadMyOrders() {
        try {
            int empId = getActorId();
            List<Order> list = orderService.getOrdersByEmployee(empId);
            myOrders.setAll(list);
            setStatus("Loaded " + list.size() + " order(s).");
        } catch (Exception e) { alert(Alert.AlertType.ERROR, "Error", e.getMessage()); }
    }

    @FXML public void handleRefresh() { txtSearch.clear(); loadMyOrders(); }

    @FXML public void handleSearch() {
        String kw = txtSearch.getText().trim().toLowerCase();
        if (kw.isBlank()) { loadMyOrders(); return; }
        ObservableList<Order> filtered = FXCollections.observableArrayList(
                myOrders.filtered(o ->
                        (o.getCustomerName() != null && o.getCustomerName().toLowerCase().contains(kw)) ||
                        (o.getStatus() != null && o.getStatus().toLowerCase().contains(kw)) ||
                        String.valueOf(o.getOrderId()).contains(kw)));
        tblMyOrders.setItems(filtered);
        setStatus("Found " + filtered.size() + " order(s).");
    }

    // ==================== CREATE ORDER DIALOG ====================
    @FXML public void handleNewOrder() {
        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Create New Order");

        ComboBox<Customer>        cbCustomer = new ComboBox<>();
        ComboBox<DeliveryAddress> cbAddress  = new ComboBox<>();
        ComboBox<Promotion>       cbPromo    = new ComboBox<>();
        ComboBox<Product>         cbProduct  = new ComboBox<>();
        TextField                 tfQty      = new TextField();
        TextArea                  taNote     = new TextArea();

        tfQty.setPromptText("Qty"); taNote.setPrefRowCount(2); taNote.setWrapText(true);

        // Declare labels FIRST before using them in lambdas
        Label lblSub   = new Label("0 ₫"); lblSub.setStyle("-fx-font-size:12px;");
        Label lblDisc  = new Label("0 ₫"); lblDisc.setStyle("-fx-font-size:12px;-fx-text-fill:#c62828;");
        Label lblFinal = new Label("0 ₫"); lblFinal.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#1a237e;");
        Label lblErr   = new Label(); lblErr.setStyle("-fx-text-fill:#c62828;-fx-font-size:12px;"); lblErr.setWrapText(true);

        // Draft lines table (declared after labels so lambda can capture them)
        ObservableList<OrderLine> draftLines = FXCollections.observableArrayList();
        TableView<OrderLine> tblDraft = buildDraftLinesTable(draftLines, () -> recalcTotals(draftLines, cbPromo, lblSub, lblDisc, lblFinal));
        tblDraft.setPrefHeight(160);

        List<Customer>        allCustomers;
        List<Promotion>       allPromos;
        List<Product>         allProducts;
        try {
            allCustomers = customerDAO.findAll();
            allPromos    = promoDAO.findActive();
            allProducts  = productDAO.getActive();
        } catch (Exception e) { alert(Alert.AlertType.ERROR, "Error", e.getMessage()); return; }

        // Searchable wrappers
        VBox vCustomer = SearchableComboBox.wrap(cbCustomer, allCustomers,
                c -> c.getCustomerId() + " – " + c.getFullName() + " (" + c.getPhone() + ")");
        VBox vPromo    = SearchableComboBox.wrap(cbPromo, allPromos,
                p -> p.getCode() + " – " + p.getName());
        VBox vProduct  = SearchableComboBox.wrap(cbProduct, allProducts,
                p -> p.getProductId() + ". " + p.getName()
                     + "  [" + String.format("%,.0f ₫", p.getPrice()) + "]");
        // Address combo – items will be reloaded when a customer is selected
        VBox vAddress  = SearchableComboBox.wrap(cbAddress, List.of(),
                a -> (a.isDefault() ? "⭐ " : "") + a.getReceiverName() + " | "
                     + a.getAddressLine() + ", " + a.getWard() + ", " + a.getDistrict());

        cbCustomer.valueProperty().addListener((obs, old, cust) -> {
            cbAddress.setValue(null);
            if (cust != null) {
                try {
                    List<DeliveryAddress> addrs = addressDAO.findByCustomerId(cust.getCustomerId());
                    cbAddress.setItems(FXCollections.observableArrayList(addrs));
                    addrs.stream().filter(DeliveryAddress::isDefault).findFirst().ifPresent(cbAddress::setValue);
                } catch (Exception ex) { alert(Alert.AlertType.ERROR, "Error", ex.getMessage()); }
            } else {
                cbAddress.setItems(FXCollections.observableArrayList());
            }
        });

        cbPromo.valueProperty().addListener((obs, o, p) -> recalcTotals(draftLines, cbPromo, lblSub, lblDisc, lblFinal));

        GridPane grid = buildGrid();
        int row = 0;
        grid.add(sec("-- Order Info"),         0, row, 2, 1); row++;
        grid.add(fl("Customer *"),   0, row); grid.add(vCustomer, 1, row++);
        grid.add(fl("Address *"),    0, row); grid.add(vAddress,  1, row++);
        grid.add(fl("Promotion"),    0, row); grid.add(vPromo,    1, row++);
        grid.add(fl("Note"),         0, row); grid.add(taNote,    1, row++);
        grid.add(new Separator(),              0, row, 2, 1); row++;
        grid.add(sec("-- Add Products"),       0, row, 2, 1); row++;

        // Add-line row
        HBox addLine = new HBox(8, vProduct, tfQty);
        HBox.setHgrow(vProduct, Priority.ALWAYS);
        Button btnAddLine = new Button("＋ Add");
        btnAddLine.setStyle("-fx-background-color:#3949ab;-fx-text-fill:white;-fx-background-radius:6;-fx-padding:6 14;");
        addLine.getChildren().add(btnAddLine);
        grid.add(addLine, 0, row, 2, 1); row++;
        grid.add(tblDraft, 0, row, 2, 1); row++;

        // Totals
        GridPane totals = new GridPane(); totals.setHgap(20); totals.setVgap(4);
        totals.setStyle("-fx-background-color:#f5f6fc;-fx-padding:10 14;-fx-background-radius:6;");
        totals.add(new Label("Subtotal:"), 0, 0); totals.add(lblSub,   1, 0);
        totals.add(new Label("Discount:"), 0, 1); totals.add(lblDisc,  1, 1);
        totals.add(new Label("Total:"),    0, 2); totals.add(lblFinal, 1, 2);
        grid.add(totals, 0, row, 2, 1); row++;
        grid.add(new Separator(), 0, row, 2, 1); row++;
        grid.add(lblErr, 0, row, 2, 1); row++;

        Button btnCreate = primaryBtn("✔  Confirm Order");
        Button btnCancel = new Button("Cancel");
        btnCancel.setStyle("-fx-background-radius:6;-fx-padding:8 18;");
        HBox btns = new HBox(10, btnCancel, btnCreate); btns.setAlignment(Pos.CENTER_RIGHT);
        grid.add(btns, 0, row, 2, 1);

        btnAddLine.setOnAction(ev -> {
            lblErr.setText("");
            Product p = cbProduct.getValue();
            if (p == null) { lblErr.setText("Please select a product."); return; }
            try {
                int qty = Integer.parseInt(tfQty.getText().trim());
                if (qty <= 0) throw new NumberFormatException();
                if (draftLines.stream().anyMatch(l -> l.getProductId() == p.getProductId())) {
                    lblErr.setText("Product already added."); return;
                }
                OrderLine line = new OrderLine(0, p.getProductId(), qty, p.getPrice());
                line.setProductName(p.getName());
                draftLines.add(line);
                recalcTotals(draftLines, cbPromo, lblSub, lblDisc, lblFinal);
                tfQty.clear();
            } catch (NumberFormatException ex) { lblErr.setText("Quantity must be a positive integer."); }
        });

        btnCreate.setOnAction(ev -> {
            lblErr.setText("");
            if (cbCustomer.getValue() == null) { lblErr.setText("Please select a customer."); return; }
            if (cbAddress.getValue() == null)  { lblErr.setText("Please select a delivery address."); return; }
            if (draftLines.isEmpty())           { lblErr.setText("Add at least one product."); return; }
            try {
                Promotion promo = cbPromo.getValue();
                Order order = new Order(
                        cbCustomer.getValue().getCustomerId(), getActorId(),
                        cbAddress.getValue().getAddressId(),
                        promo != null ? promo.getPromoId() : null,
                        taNote.getText().trim().isBlank() ? null : taNote.getText().trim());
                int newId = orderService.createOrder(order, new ArrayList<>(draftLines));
                setStatus("Order #" + newId + " created successfully.");
                NotificationUtil.success(tblMyOrders, "Order #" + newId + " created!");
                loadMyOrders(); dlg.close();
            } catch (Exception ex) { lblErr.setText(ex.getMessage()); }
        });
        btnCancel.setOnAction(ev -> dlg.close());

        ScrollPane scroll = new ScrollPane(grid); scroll.setFitToWidth(true);
        dlg.setScene(new Scene(scroll, 560, 720)); dlg.showAndWait();
    }

    // ==================== VIEW DETAIL DIALOG ====================
    @FXML public void handleViewDetail() {
        Order sel = tblMyOrders.getSelectionModel().getSelectedItem();
        if (sel != null) openDetailDialog(sel);
    }

    private void openDetailDialog(Order order) {
        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Order Detail - #" + order.getOrderId());

        TableView<OrderLine> tblLines = new TableView<>();
        TableColumn<OrderLine, String>     cProd  = new TableColumn<>("Product");
        TableColumn<OrderLine, Integer>    cQty   = new TableColumn<>("Qty");
        TableColumn<OrderLine, BigDecimal> cPrice = new TableColumn<>("Unit Price");
        TableColumn<OrderLine, BigDecimal> cTotal = new TableColumn<>("Subtotal");
        cProd.setCellValueFactory(new PropertyValueFactory<>("productName")); cProd.setPrefWidth(220);
        cQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));     cQty.setPrefWidth(55);
        cPrice.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));  cPrice.setPrefWidth(130);
        cTotal.setCellValueFactory(new PropertyValueFactory<>("lineTotal"));  cTotal.setPrefWidth(130);
        for (TableColumn<OrderLine, BigDecimal> col : List.of(cPrice, cTotal)) {
            col.setCellFactory(c -> new TableCell<>() {
                @Override protected void updateItem(BigDecimal v, boolean empty) {
                    super.updateItem(v, empty);
                    setText(empty || v == null ? null : String.format("%,.0f ₫", v));
                }
            });
        }
        tblLines.getColumns().addAll(cProd, cQty, cPrice, cTotal);
        tblLines.setPrefHeight(200);

        try {
            tblLines.setItems(FXCollections.observableArrayList(
                    orderService.getLinesForOrder(order.getOrderId())));
        } catch (Exception e) { alert(Alert.AlertType.ERROR, "Error", e.getMessage()); return; }

        GridPane info = new GridPane();
        info.setHgap(16); info.setVgap(8); info.setPadding(new Insets(24));
        info.getColumnConstraints().addAll(new ColumnConstraints(130), new ColumnConstraints(280));

        int row = 0;
        Label lblTitle = new Label("Order #" + order.getOrderId());
        lblTitle.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#1a237e;");
        info.add(lblTitle,                   0, row, 2, 1); row++;
        info.add(new Separator(),            0, row, 2, 1); row++;
        info.add(sec("-- Order Info"),       0, row, 2, 1); row++;
        info.add(fl("Customer"), 0, row); info.add(val(nvl(order.getCustomerName())), 1, row++);
        info.add(fl("Date"),     0, row); info.add(val(order.getOrderDate() != null ? order.getOrderDate().format(DTF) : "—"), 1, row++);
        info.add(fl("Status"),   0, row);
        Label lblStatus = new Label(nvl(order.getStatus()));
        lblStatus.setStyle("-fx-font-weight:bold;-fx-text-fill:#1565c0;");
        info.add(lblStatus, 1, row++);
        info.add(fl("Total"),    0, row);
        Label lblTotal = new Label(order.getFinalTotal() != null ? String.format("%,.0f ₫", order.getFinalTotal()) : "—");
        lblTotal.setStyle("-fx-font-size:14px;-fx-font-weight:bold;-fx-text-fill:#27ae60;");
        info.add(lblTotal, 1, row++);
        info.add(new Separator(),            0, row, 2, 1); row++;
        info.add(sec("-- Order Items"),      0, row, 2, 1); row++;
        info.add(tblLines,                   0, row, 2, 1); row++;

        Button btnClose = primaryBtn("Close");
        btnClose.setOnAction(ev -> dlg.close());
        HBox btns = new HBox(btnClose); btns.setAlignment(Pos.CENTER_RIGHT);
        btns.setPadding(new Insets(0, 24, 16, 24));

        VBox root = new VBox();
        ScrollPane scroll = new ScrollPane(info); scroll.setFitToWidth(true);
        root.getChildren().addAll(scroll, btns);
        dlg.setScene(new Scene(root, 560, 520)); dlg.showAndWait();
    }

    // ==================== UPDATE STATUS DIALOG ====================
    @FXML public void handleUpdateStatus() {
        Order sel = tblMyOrders.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        // Guard: terminal statuses cannot be updated
        String cur = sel.getStatus() != null ? sel.getStatus() : "";
        if (cur.equals("COMPLETED") || cur.equals("CANCELLED") || cur.equals("RETURNED")) {
            alert(Alert.AlertType.WARNING, "Cannot Update",
                    "Order #" + sel.getOrderId() + " is already " + cur + " and cannot be changed.");
            return;
        }

        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Update Status – Order #" + sel.getOrderId());

        // Matches exactly the transitions defined in OrderService.validateTransition():
        //   DRAFT      → CONFIRMED | CANCELLED
        //   CONFIRMED  → PAID      | CANCELLED
        //   PAID       → DELIVERING
        //   DELIVERING → COMPLETED | RETURNED
        //   (no PENDING state, no invalid transitions)
        List<String> allowed = switch (cur) {
            case "DRAFT"      -> List.of("CONFIRMED", "CANCELLED");
            case "CONFIRMED"  -> List.of("PAID", "CANCELLED");
            case "PAID"       -> List.of("DELIVERING");
            case "DELIVERING" -> List.of("COMPLETED", "RETURNED");
            default           -> List.of(); // unrecognised status: no transitions allowed
        };

        if (allowed.isEmpty()) {
            alert(Alert.AlertType.WARNING, "Cannot Update",
                "Order #" + sel.getOrderId() + " has an unrecognised status: '" + cur + "'.");
            return;
        }

        ComboBox<String> cbStatus = new ComboBox<>(FXCollections.observableArrayList(allowed));
        cbStatus.setMaxWidth(Double.MAX_VALUE);
        cbStatus.setPromptText("— Select new status —");

        TextArea taNoteStatus = new TextArea();
        taNoteStatus.setPromptText("Optional: reason or note for this status change…");
        taNoteStatus.setPrefRowCount(3);
        taNoteStatus.setWrapText(true);
        taNoteStatus.setMaxWidth(Double.MAX_VALUE);

        // Current status badge
        Label lblCurrentBadge = new Label(cur);
        lblCurrentBadge.setStyle(switch (cur) {
            case "CONFIRMED"  -> "-fx-background-color:#ede7f6;-fx-text-fill:#6a1b9a;-fx-font-weight:bold;-fx-padding:4 10;-fx-background-radius:6;";
            case "PAID"       -> "-fx-background-color:#e3f2fd;-fx-text-fill:#1565c0;-fx-font-weight:bold;-fx-padding:4 10;-fx-background-radius:6;";
            case "DELIVERING" -> "-fx-background-color:#fff3e0;-fx-text-fill:#e65100;-fx-font-weight:bold;-fx-padding:4 10;-fx-background-radius:6;";
            default           -> "-fx-background-color:#f5f5f5;-fx-text-fill:#555;-fx-font-weight:bold;-fx-padding:4 10;-fx-background-radius:6;";
        });

        Label lblErr = new Label();
        lblErr.setStyle("-fx-text-fill:#c62828;-fx-font-size:12px;"); lblErr.setWrapText(true);

        // ── Build layout with proper spacing (match Admin dialog style) ──
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        grid.setPadding(new Insets(28, 28, 20, 28));
        grid.getColumnConstraints().addAll(new ColumnConstraints(145), new ColumnConstraints(280));

        int row = 0;
        grid.add(sec("── Order #" + sel.getOrderId()),  0, row, 2, 1); row++;
        grid.add(fl("Current Status"), 0, row); grid.add(lblCurrentBadge, 1, row++);
        grid.add(new Separator(),                       0, row, 2, 1); row++;
        grid.add(fl("New Status *"),   0, row); grid.add(cbStatus,        1, row++);

        // Note label aligned to top of TextArea
        Label lblNote = fl("Note");
        GridPane.setValignment(lblNote, javafx.geometry.VPos.TOP);
        GridPane.setMargin(lblNote, new Insets(4, 0, 0, 0));
        grid.add(lblNote,              0, row); grid.add(taNoteStatus,    1, row++);

        grid.add(new Separator(),                       0, row, 2, 1); row++;
        grid.add(lblErr,                                0, row, 2, 1); row++;

        Button btnSave   = primaryBtn("Update Status");
        Button btnCancel = new Button("Cancel");
        btnCancel.setStyle("-fx-background-radius:6;-fx-padding:8 18;");
        HBox btns = new HBox(10, btnCancel, btnSave);
        btns.setAlignment(Pos.CENTER_RIGHT);
        btns.setPadding(new Insets(6, 0, 0, 0));
        grid.add(btns, 0, row, 2, 1);

        btnSave.setOnAction(ev -> {
            String newStatus = cbStatus.getValue();
            if (newStatus == null) { lblErr.setText("Please select a new status."); return; }
            try {
                String note = taNoteStatus.getText().trim();
                orderService.updateStatus(sel.getOrderId(), newStatus, getActorId(),
                        note.isBlank() ? null : note);
                setStatus("Order #" + sel.getOrderId() + "  " + cur + " → " + newStatus);
                NotificationUtil.success(tblMyOrders, "Order #" + sel.getOrderId() + " → " + newStatus);
                loadMyOrders(); dlg.close();
            } catch (Exception ex) { lblErr.setText(ex.getMessage()); }
        });
        btnCancel.setOnAction(ev -> dlg.close());

        dlg.setScene(new Scene(grid, 490, 380)); dlg.setResizable(false); dlg.showAndWait();
    }

    // ==================== BILLING DIALOG ====================
    @FXML public void handleManageBilling() {
        Order sel = tblMyOrders.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Invoice - Order #" + sel.getOrderId());

        ComboBox<String> cbPayMethod     = new ComboBox<>(FXCollections.observableArrayList("CASH","BANK_TRANSFER","CARD","OTHER"));
        ComboBox<String> cbBillingStatus = new ComboBox<>(FXCollections.observableArrayList("UNPAID","PARTIAL","PAID"));
        TextField        tfAmount        = new TextField();
        TextArea         taNote          = new TextArea(); taNote.setPrefRowCount(3); taNote.setWrapText(true);
        cbPayMethod.setMaxWidth(Double.MAX_VALUE); cbBillingStatus.setMaxWidth(Double.MAX_VALUE);

        Label lblInfo = new Label(); lblInfo.setStyle("-fx-font-size:12px;-fx-text-fill:#555;"); lblInfo.setWrapText(true);
        Label lblErr  = new Label(); lblErr.setStyle("-fx-text-fill:#c62828;-fx-font-size:12px;"); lblErr.setWrapText(true);

        Billing[] existing = {null};
        try { existing[0] = orderService.getBillingByOrder(sel.getOrderId()); }
        catch (Exception e) { alert(Alert.AlertType.ERROR, "Error", e.getMessage()); return; }

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
        grid.add(fl("Amount Collected"), 0, row); grid.add(tfAmount,        1, row++);
        grid.add(fl("Invoice Status *"), 0, row); grid.add(cbBillingStatus, 1, row++);
        grid.add(fl("Note"),             0, row); grid.add(taNote,          1, row++);
        grid.add(new Separator(),                   0, row, 2, 1); row++;
        grid.add(lblErr,                            0, row, 2, 1); row++;

        Button btnSave   = primaryBtn(btnLabel);
        Button btnCancel = new Button("Cancel");
        btnCancel.setStyle("-fx-background-radius:6;-fx-padding:8 18;");
        HBox btns = new HBox(10, btnCancel, btnSave); btns.setAlignment(Pos.CENTER_RIGHT);
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
                    orderService.updateBilling(existing[0], false);
                }
                setStatus("Invoice saved for Order #" + sel.getOrderId());
                NotificationUtil.success(tblMyOrders, "Invoice saved for Order #" + sel.getOrderId());
                loadMyOrders(); dlg.close();
            } catch (NumberFormatException ex) { lblErr.setText("Amount must be a valid number."); }
              catch (Exception ex) { lblErr.setText(ex.getMessage()); }
        });
        btnCancel.setOnAction(ev -> dlg.close());

        ScrollPane scroll = new ScrollPane(grid); scroll.setFitToWidth(true);
        dlg.setScene(new Scene(scroll, 480, 440)); dlg.showAndWait();
    }

    // ── Draft lines table builder ──────────────────────────────────────────
    private TableView<OrderLine> buildDraftLinesTable(ObservableList<OrderLine> lines, Runnable onRemove) {
        TableView<OrderLine> tbl = new TableView<>();
        TableColumn<OrderLine, String>     cProd   = new TableColumn<>("Product");
        TableColumn<OrderLine, Integer>    cQty    = new TableColumn<>("Qty");
        TableColumn<OrderLine, BigDecimal> cPrice  = new TableColumn<>("Unit Price");
        TableColumn<OrderLine, BigDecimal> cTotal  = new TableColumn<>("Subtotal");
        TableColumn<OrderLine, Void>       cRemove = new TableColumn<>("");

        cProd.setCellValueFactory(new PropertyValueFactory<>("productName")); cProd.setPrefWidth(185);
        cQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));     cQty.setPrefWidth(50);
        cPrice.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));  cPrice.setPrefWidth(110);
        cTotal.setCellValueFactory(new PropertyValueFactory<>("lineTotal"));  cTotal.setPrefWidth(110);

        for (TableColumn<OrderLine, BigDecimal> col : List.of(cPrice, cTotal)) {
            col.setCellFactory(c -> new TableCell<>() {
                @Override protected void updateItem(BigDecimal v, boolean empty) {
                    super.updateItem(v, empty);
                    setText(empty || v == null ? null : String.format("%,.0f ₫", v));
                }
            });
        }

        cRemove.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("✖");
            { btn.setStyle("-fx-background-color:#c62828;-fx-text-fill:white;-fx-background-radius:4;-fx-padding:3 8;");
              btn.setOnAction(e -> { lines.remove(getTableView().getItems().get(getIndex())); onRemove.run(); }); }
            @Override protected void updateItem(Void v, boolean empty) { super.updateItem(v, empty); setGraphic(empty ? null : btn); }
        });
        cRemove.setPrefWidth(55);

        tbl.getColumns().addAll(cProd, cQty, cPrice, cTotal, cRemove);
        tbl.setItems(lines);
        return tbl;
    }

    private void recalcTotals(ObservableList<OrderLine> lines, ComboBox<Promotion> cbPromo,
                               Label lblSub, Label lblDisc, Label lblFinal) {
        BigDecimal sub = lines.stream()
                .map(l -> l.getUnitPrice().multiply(BigDecimal.valueOf(l.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal disc = BigDecimal.ZERO;
        Promotion promo = cbPromo.getValue();
        if (promo != null) {
            disc = "PERCENT".equals(promo.getDiscountType())
                    ? sub.multiply(promo.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                    : promo.getDiscountValue().min(sub);
        }
        BigDecimal fin = sub.subtract(disc).max(BigDecimal.ZERO);
        lblSub.setText(String.format("%,.0f ₫", sub));
        lblDisc.setText(String.format("%,.0f ₫", disc));
        lblFinal.setText(String.format("%,.0f ₫", fin));
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private int getActorId() {
        Employee emp = SessionManager.getInstance().getCurrentEmployee();
        if (emp == null) throw new IllegalStateException("Invalid session – employee not found.");
        return emp.getEmployeeId();
    }

    private GridPane buildGrid() {
        GridPane g = new GridPane(); g.setHgap(12); g.setVgap(10); g.setPadding(new Insets(24));
        g.getColumnConstraints().addAll(new ColumnConstraints(145), new ColumnConstraints(300));
        return g;
    }

    private Label fl(String t)   { Label l = new Label(t); l.setStyle("-fx-font-weight:bold;-fx-font-size:12px;"); return l; }
    private Label sec(String t)  { Label l = new Label(t); l.setStyle("-fx-font-weight:bold;-fx-font-size:11px;-fx-text-fill:#3949ab;"); return l; }
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