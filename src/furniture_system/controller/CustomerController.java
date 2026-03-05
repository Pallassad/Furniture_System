package furniture_system.controller;

import furniture_system.model.Customer;
import furniture_system.model.Customer.Gender;
import furniture_system.model.Customer.Status;
import furniture_system.model.OrderSummary;
import furniture_system.model.OrderSummary.OrderLineItem;
import furniture_system.service.CustomerService;
import furniture_system.utils.SessionManager;
import javafx.beans.property.*;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class CustomerController {

    private static final DateTimeFormatter DT_FMT  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final NumberFormat      CUR_FMT  =
            NumberFormat.getNumberInstance(Locale.of("vi", "VN")); // ✅ fix deprecated new Locale()

    // ── Table ─────────────────────────────────────────────────────────────────
    @FXML private TableView<Customer>            customerTable;
    @FXML private TableColumn<Customer, Integer> colId;
    @FXML private TableColumn<Customer, String>  colFullName;
    @FXML private TableColumn<Customer, String>  colPhone;
    @FXML private TableColumn<Customer, String>  colEmail;
    @FXML private TableColumn<Customer, String>  colGender;
    @FXML private TableColumn<Customer, String>  colStatus;
    @FXML private TableColumn<Customer, String>  colCreatedAt;

    // ── Toolbar ───────────────────────────────────────────────────────────────
    @FXML private TextField searchField;
    @FXML private Button    btnAdd;
    @FXML private Button    btnEdit;
    @FXML private Button    btnDelete;
    @FXML private Button    btnHistory;
    @FXML private Button    btnStats;
    @FXML private Button    btnRefresh;
    @FXML private Label     statusBarLabel;

    // ── State ─────────────────────────────────────────────────────────────────
    private final CustomerService          service = new CustomerService();
    private final ObservableList<Customer> data    = FXCollections.observableArrayList();
    private final boolean                  isAdmin = SessionManager.getInstance().isAdmin();

    // ─────────────────────────────────────────────────────────────────────────
    @FXML
    public void initialize() {
        setupColumns();
        loadAll();

        customerTable.getSelectionModel().selectedItemProperty().addListener((o, old, sel) -> {
            boolean has = sel != null;
            if (btnEdit    != null) btnEdit.setDisable(!has || !isAdmin);
            if (btnDelete  != null) btnDelete.setDisable(!has || !isAdmin);
            if (btnHistory != null) btnHistory.setDisable(!has);
        });
        if (btnEdit    != null) btnEdit.setDisable(true);
        if (btnDelete  != null) btnDelete.setDisable(true);
        if (btnHistory != null) btnHistory.setDisable(true);
        if (btnAdd     != null) btnAdd.setVisible(isAdmin);
        if (btnStats   != null) btnStats.setVisible(isAdmin);

        searchField.textProperty().addListener((o, old, v) -> handleSearch());
    }

    // ── Columns ───────────────────────────────────────────────────────────────
    private void setupColumns() {
        colId.setCellValueFactory(c ->
                new SimpleIntegerProperty(c.getValue().getCustomerId()).asObject());
        colFullName.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getFullName()));
        colPhone.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getPhone()));
        colEmail.setCellValueFactory(c ->
                new SimpleStringProperty(nvl(c.getValue().getEmail())));
        colGender.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getGender() != null
                        ? c.getValue().getGender().name() : "—"));
        colStatus.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getStatus().name()));
        colCreatedAt.setCellValueFactory(c -> {
            var dt = c.getValue().getCreatedAt();
            return new SimpleStringProperty(dt != null ? dt.format(DT_FMT) : "—");
        });

        // Row colouring: INACTIVE = grey ✅ typed TableRow — no raw type
        customerTable.setRowFactory(tv -> new TableRow<Customer>() {
            @Override
            protected void updateItem(Customer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setStyle(""); return; }
                setStyle(item.getStatus() == Status.INACTIVE
                        ? "-fx-background-color:#e8e8e8;" : "");
            }
        });
        customerTable.setItems(data);
    }

    private void loadAll() {
        try {
            data.setAll(service.getAll());
            setStatus("Loaded " + data.size() + " customer(s).");
        } catch (Exception e) {
            showError("Load failed", e.getMessage());
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────
    @FXML public void handleSearch() {
        try {
            data.setAll(service.search(searchField.getText()));
            setStatus("Found " + data.size() + " result(s).");
        } catch (Exception e) {
            showError("Search error", e.getMessage());
        }
    }

    @FXML public void handleRefresh() { searchField.clear(); loadAll(); }

    // ── Add ───────────────────────────────────────────────────────────────────
    @FXML public void handleAdd() { openForm(null); }

    // ── Edit ──────────────────────────────────────────────────────────────────
    @FXML public void handleEdit() {
        Customer sel = customerTable.getSelectionModel().getSelectedItem();
        if (sel != null) openForm(sel);
    }

    // ── Delete / Deactivate ───────────────────────────────────────────────────
    @FXML public void handleDelete() {
        Customer sel = customerTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Remove customer [" + sel.getFullName() + "]?\n\n" +
                "• Has orders → set INACTIVE\n• No orders → permanently deleted",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Remove");
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.YES) return;
            try {
                String r = service.removeCustomer(sel.getCustomerId());
                setStatus(r.equals("SOFT")
                        ? "Customer set to INACTIVE (has order history)."
                        : "Customer permanently deleted.");
                loadAll();
            } catch (Exception e) {
                showError("Remove failed", e.getMessage());
            }
        });
    }

    // ── Purchase History ──────────────────────────────────────────────────────
    @FXML public void handleHistory() {
        Customer sel = customerTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        try {
            List<OrderSummary> orders = service.getPurchaseHistory(sel.getCustomerId());
            showHistoryDialog(sel, orders);
        } catch (Exception e) {
            showError("History error", e.getMessage());
        }
    }

    // ── Statistics ────────────────────────────────────────────────────────────
    @FXML public void handleStats() {
        Stage dlg = new Stage();
        dlg.setTitle("Customer Statistics");
        dlg.initModality(Modality.APPLICATION_MODAL);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(buildStatusTab(), buildGenderTab(),
                              buildMonthTab(),  buildTopTab());
        dlg.setScene(new Scene(tabs, 640, 420));
        dlg.showAndWait();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FORM DIALOG
    // ─────────────────────────────────────────────────────────────────────────
    private void openForm(Customer existing) {
        boolean isEdit = existing != null;
        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle(isEdit ? "Edit Customer" : "Add New Customer");

        TextField        tfName    = new TextField();
        TextField        tfPhone   = new TextField();
        TextField        tfEmail   = new TextField();
        ComboBox<String> cbGender  = new ComboBox<>(
                FXCollections.observableArrayList("—", "MALE", "FEMALE", "OTHER"));
        ComboBox<Status> cbStatus  = new ComboBox<>(
                FXCollections.observableArrayList(Status.values()));
        Label lblErr = new Label();
        lblErr.setStyle("-fx-text-fill:#c62828; -fx-font-size:12px;");
        lblErr.setWrapText(true);

        // Pre-fill
        if (isEdit) {
            tfName.setText(existing.getFullName());
            tfPhone.setText(existing.getPhone());
            tfEmail.setText(nvl(existing.getEmail()));
            cbGender.setValue(existing.getGender() != null ? existing.getGender().name() : "—");
            cbStatus.setValue(existing.getStatus());
        } else {
            cbGender.setValue("—");
            cbStatus.setValue(Status.ACTIVE);
        }
        if (!isAdmin) { cbStatus.setDisable(true); cbStatus.setValue(Status.ACTIVE); }

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(10);
        grid.setPadding(new Insets(24));
        grid.getColumnConstraints().addAll(
                new ColumnConstraints(110), new ColumnConstraints(260));

        int row = 0;
        grid.add(lbl("Full Name *"), 0, row); grid.add(tfName,   1, row++);
        grid.add(lbl("Phone *"),     0, row); grid.add(tfPhone,  1, row++);
        grid.add(lbl("Email"),       0, row); grid.add(tfEmail,  1, row++);
        grid.add(lbl("Gender"),      0, row); grid.add(cbGender, 1, row++);
        grid.add(lbl("Status *"),    0, row); grid.add(cbStatus, 1, row++);
        grid.add(lblErr,             0, row, 2, 1); row++;

        Button btnSave   = new Button(isEdit ? "💾  Save Changes" : "➕  Add Customer");
        Button btnCancel = new Button("Cancel");
        btnSave.setStyle("-fx-background-color:#3949ab; -fx-text-fill:white;" +
                " -fx-background-radius:6; -fx-padding:8 18; -fx-font-weight:bold;");
        btnCancel.setStyle("-fx-background-radius:6; -fx-padding:8 18;");
        HBox btns = new HBox(10, btnCancel, btnSave);
        btns.setAlignment(Pos.CENTER_RIGHT);
        grid.add(btns, 0, row, 2, 1);

        cbGender.setMaxWidth(Double.MAX_VALUE);
        cbStatus.setMaxWidth(Double.MAX_VALUE);

        btnSave.setOnAction(ev -> {
            lblErr.setText("");
            try {
                Customer c = isEdit ? existing : new Customer();
                c.setFullName(tfName.getText().trim());
                c.setPhone(tfPhone.getText().trim());
                c.setEmail(tfEmail.getText().trim());
                String gv = cbGender.getValue();
                c.setGender(("—".equals(gv) || gv == null) ? null : Gender.valueOf(gv));
                c.setStatus(cbStatus.getValue());

                if (isEdit) {
                    service.updateCustomer(c);
                    setStatus("Customer [" + c.getFullName() + "] updated.");
                } else {
                    int id = isAdmin ? service.addCustomer(c) : service.createCustomer(c);
                    setStatus("Customer added with ID " + id + ".");
                }
                loadAll();
                dlg.close();
            } catch (IllegalArgumentException ex) {
                lblErr.setText("⚠  " + ex.getMessage());
            } catch (Exception ex) {
                lblErr.setText("Error: " + ex.getMessage());
            }
        });
        btnCancel.setOnAction(ev -> dlg.close());
        dlg.setScene(new Scene(grid));
        dlg.setResizable(false);
        dlg.showAndWait();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PURCHASE HISTORY DIALOG
    // ─────────────────────────────────────────────────────────────────────────
    private void showHistoryDialog(Customer customer, List<OrderSummary> orders) {
        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Purchase History – " + customer.getFullName());

        VBox root = new VBox(12);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color:#f5f6fa;");

        // Customer info bar
        HBox info = new HBox(20);
        info.setStyle("-fx-background-color:#e8eaf6; -fx-background-radius:6; -fx-padding:10 14;");
        info.getChildren().addAll(
                infoChip("ID",     String.valueOf(customer.getCustomerId())),
                infoChip("Name",   customer.getFullName()),
                infoChip("Phone",  customer.getPhone()),
                infoChip("Email",  nvl(customer.getEmail())),
                infoChip("Orders", String.valueOf(orders.size()))
        );
        root.getChildren().add(info);

        if (orders.isEmpty()) {
            Label empty = new Label("No purchase history found.");
            empty.setStyle("-fx-font-size:14px; -fx-text-fill:#9e9e9e; -fx-font-style:italic;");
            root.getChildren().add(empty);
        } else {
            BigDecimal totalSpent = orders.stream()
                    .filter(o -> !List.of("CANCELLED", "RETURNED").contains(o.getStatus()))
                    .map(OrderSummary::getFinalTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Label lblTotal = new Label("Total Spent (excl. cancelled): "
                    + CUR_FMT.format(totalSpent) + " VND");
            lblTotal.setStyle("-fx-font-weight:bold; -fx-font-size:13px; -fx-text-fill:#1a237e;");
            root.getChildren().add(lblTotal);

            ScrollPane scroll = new ScrollPane();
            scroll.setFitToWidth(true);
            VBox orderList = new VBox(10);
            orderList.setPadding(new Insets(4));
            for (OrderSummary o : orders) {
                orderList.getChildren().add(buildOrderPane(o));
            }
            scroll.setContent(orderList);
            VBox.setVgrow(scroll, Priority.ALWAYS);
            root.getChildren().add(scroll);
        }

        Button btnClose = new Button("Close");
        btnClose.setStyle("-fx-background-color:#3949ab; -fx-text-fill:white;" +
                " -fx-background-radius:6; -fx-padding:8 20;");
        btnClose.setOnAction(e -> dlg.close());
        HBox footer = new HBox(btnClose);
        footer.setAlignment(Pos.CENTER_RIGHT);
        root.getChildren().add(footer);

        dlg.setScene(new Scene(root, 750, 560));
        dlg.showAndWait();
    }

    private TitledPane buildOrderPane(OrderSummary o) {
        String orderStatus   = o.getStatus();
        String billingStatus = o.getBillingStatus() != null ? o.getBillingStatus() : "—";
        String color = switch (orderStatus) {
            case "COMPLETED"            -> "#1b5e20";
            case "CANCELLED", "RETURNED"-> "#b71c1c";
            case "DELIVERING"           -> "#e65100";
            default                     -> "#1a237e";
        };

        String title = String.format("Order #%d  |  %s  |  %s  |  💰 %s VND  |  🧾 %s",
                o.getOrderId(),
                o.getOrderDate() != null ? o.getOrderDate().format(DT_FMT) : "—",
                orderStatus,
                CUR_FMT.format(o.getFinalTotal()),
                billingStatus);

        VBox content = new VBox(8);
        content.setPadding(new Insets(10));

        HBox summary = new HBox(24);
        summary.getChildren().addAll(
                chip("SubTotal", CUR_FMT.format(o.getSubTotal())   + " VND"),
                chip("Discount", CUR_FMT.format(o.getDiscount())   + " VND"),
                chip("Final",    CUR_FMT.format(o.getFinalTotal()) + " VND"),
                chip("Payment",  nvl(o.getPaymentMethod()))
        );
        content.getChildren().add(summary);

        if (o.getLines() != null && !o.getLines().isEmpty()) {
            // ✅ fully typed TableView — no raw/unchecked
            TableView<OrderLineItem> tbl = new TableView<>();
            tbl.setPrefHeight(110);
            tbl.setStyle("-fx-font-size:12px;");

            TableColumn<OrderLineItem, String>  cProd  = new TableColumn<>("Product");
            TableColumn<OrderLineItem, Integer> cQty   = new TableColumn<>("Qty");
            TableColumn<OrderLineItem, String>  cPrice = new TableColumn<>("Unit Price");
            TableColumn<OrderLineItem, String>  cTotal = new TableColumn<>("Line Total");
            cProd.setPrefWidth(220); cQty.setPrefWidth(60);
            cPrice.setPrefWidth(130); cTotal.setPrefWidth(130);

            cProd.setCellValueFactory(c  ->
                    new SimpleStringProperty(c.getValue().getProductName()));
            cQty.setCellValueFactory(c   ->
                    new SimpleIntegerProperty(c.getValue().getQuantity()).asObject());
            cPrice.setCellValueFactory(c ->
                    new SimpleStringProperty(CUR_FMT.format(c.getValue().getUnitPrice())));
            cTotal.setCellValueFactory(c ->
                    new SimpleStringProperty(CUR_FMT.format(c.getValue().getLineTotal())));

            tbl.getColumns().addAll(cProd, cQty, cPrice, cTotal);
            tbl.setItems(FXCollections.observableArrayList(o.getLines()));
            content.getChildren().add(tbl);
        }

        if (o.getNote() != null && !o.getNote().isBlank()) {
            content.getChildren().add(new Label("📝 Note: " + o.getNote()));
        }

        TitledPane pane = new TitledPane(title, content);
        pane.setExpanded(false);
        pane.setStyle("-fx-text-fill:" + color + "; -fx-font-weight:bold;");
        return pane;
    }

    // ── Stat tabs ─────────────────────────────────────────────────────────────
    private Tab buildStatusTab() {
        PieChart chart = new PieChart();
        chart.setTitle("Customers by Status");
        // ✅ cast to Number then doubleValue() — no unchecked warning
        service.countByStatus().forEach(r ->
                chart.getData().add(new PieChart.Data(
                        (String) r[0],
                        ((Number) r[1]).doubleValue())));
        return new Tab("By Status", chart);
    }

    private Tab buildGenderTab() {
        PieChart chart = new PieChart();
        chart.setTitle("Customers by Gender");
        service.countByGender().forEach(r ->
                chart.getData().add(new PieChart.Data(
                        (String) r[0],
                        ((Number) r[1]).doubleValue()))); // ✅
        return new Tab("By Gender", chart);
    }

    private Tab buildMonthTab() {
        BarChart<String, Number> chart =
                new BarChart<>(new CategoryAxis(), new NumberAxis());
        chart.setTitle("New Customers per Month (last 12 months)");
        chart.setLegendVisible(false);
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        service.newByMonth().forEach(r ->
                s.getData().add(new XYChart.Data<>(
                        (String) r[0],
                        ((Number) r[1]).intValue()))); // ✅
        chart.getData().add(s);
        return new Tab("New by Month", chart);
    }

    private Tab buildTopTab() {
        BarChart<Number, String> chart =
                new BarChart<>(new NumberAxis(), new CategoryAxis());
        chart.setTitle("Top 5 Customers by Spending");
        chart.setLegendVisible(false);
        XYChart.Series<Number, String> s = new XYChart.Series<>();
        service.topSpenders().forEach(r ->
                s.getData().add(new XYChart.Data<>(
                        ((Number) r[1]).doubleValue(), // ✅ thay (BigDecimal)
                        (String) r[0])));
        chart.getData().add(s);
        return new Tab("Top Spenders", chart);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private Label lbl(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-font-weight:bold; -fx-font-size:12px;");
        return l;
    }

    private HBox infoChip(String k, String v) {
        Label l = new Label(k + ": ");
        l.setStyle("-fx-font-size:12px; -fx-text-fill:#555;");
        Label val = new Label(v);
        val.setStyle("-fx-font-size:12px; -fx-font-weight:bold; -fx-text-fill:#1a237e;");
        return new HBox(2, l, val);
    }

    private VBox chip(String k, String v) {
        Label lk = new Label(k);
        lk.setStyle("-fx-font-size:10px; -fx-text-fill:#888;");
        Label lv = new Label(v);
        lv.setStyle("-fx-font-size:12px; -fx-font-weight:bold;");
        return new VBox(1, lk, lv);
    }

    private String nvl(String s)      { return s == null ? "" : s; }
    private void   setStatus(String m) { if (statusBarLabel != null) statusBarLabel.setText(m); }
    private void   showError(String t, String m) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle(t); a.setHeaderText(null); a.setContentText(m); a.showAndWait();
    }
}