package furniture_system.controller;

import furniture_system.config.DatabaseConfig;
import furniture_system.model.Product;
import furniture_system.model.Stock;
import furniture_system.model.StockLog;
import furniture_system.model.StockLog.LogType;
import furniture_system.service.ProductService;
import furniture_system.service.StockService;
import furniture_system.utils.SessionManager;
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

import java.sql.*;
import java.time.LocalTime;
import java.util.List;

public class StockController {

    // ── Stock Table ────────────────────────────────────────────────────────
    @FXML private TableView<Stock>            tblStock;
    @FXML private TableColumn<Stock, String>  colStockProduct;
    @FXML private TableColumn<Stock, Integer> colStockQty;
    @FXML private TableColumn<Stock, Integer> colStockReorder;
    @FXML private TableColumn<Stock, String>  colStockUpdated;
    @FXML private TableColumn<Stock, String>  colStockAlert;

    // ── Log Table ──────────────────────────────────────────────────────────
    @FXML private TableView<StockLog>            tblLog;
    @FXML private TableColumn<StockLog, Integer> colLogId;
    @FXML private TableColumn<StockLog, String>  colLogProduct;
    @FXML private TableColumn<StockLog, Integer> colLogQty;
    @FXML private TableColumn<StockLog, String>  colLogType;
    @FXML private TableColumn<StockLog, String>  colLogNote;
    @FXML private TableColumn<StockLog, String>  colLogActor;
    @FXML private TableColumn<StockLog, String>  colLogDate;

    // ── Log Filter ─────────────────────────────────────────────────────────
    @FXML private ComboBox<Product>  cbLogProduct;
    @FXML private ComboBox<String>   cbLogType;
    @FXML private DatePicker         dpFrom;
    @FXML private DatePicker         dpTo;

    // ── Toolbar ────────────────────────────────────────────────────────────
    @FXML private Button btnStockIn;
    @FXML private Button btnAdjust;
    @FXML private Label  lblStatus;

    private final StockService           service = new StockService();
    private final ObservableList<Stock>  stockData = FXCollections.observableArrayList();
    private final ObservableList<StockLog> logData = FXCollections.observableArrayList();

    /**
     * actorId = Employee.EmployeeId of the logged-in user.
     * -1 means no Employee row found; write operations will be blocked.
     */
    private int actorId = -1;

    // ══════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        resolveActorId();
        setupStockTable();
        setupLogTable();
        setupLogFilter();
        loadProducts();
        loadStock();
        loadLog();

        if (actorId <= 0) {
            setStatus("⚠ No Employee record linked to this account. Stock In/Adjust disabled.", true);
            btnStockIn.setDisable(true);
            btnAdjust.setDisable(true);
        }
    }

    // ── Resolve actorId ────────────────────────────────────────────────────
    // actorId PHẢI là Employee.EmployeeId (FK constraint trong bảng StockLog).
    // Nếu không tìm thấy Employee row → actorId = -1 → disable Stock In/Adjust.
    private void resolveActorId() {
        // Ưu tiên 1: lấy từ SessionManager (LoginController đã set sau đăng nhập)
        var emp = SessionManager.getInstance().getCurrentEmployee();
        if (emp != null) {
            actorId = emp.getEmployeeId();
            return;
        }

        // Ưu tiên 2: tra DB theo AccountId (trường hợp SessionManager chưa set Employee)
        var account = SessionManager.getInstance().getCurrentAccount();
        if (account == null) return;

        try (Connection con = DatabaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT EmployeeId FROM Employee WHERE AccountId = ?")) {
            ps.setInt(1, account.getAccountId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    actorId = rs.getInt("EmployeeId");
                }
                // Nếu không có Employee row (kể cả Admin) → actorId giữ -1.
                // KHÔNG fallback dùng AccountId vì đó là FK violation.
            }
        } catch (Exception ignored) {}
    }

    // ── Table setup ────────────────────────────────────────────────────────
    private void setupStockTable() {
        colStockProduct.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("productName"));
        colStockQty.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("quantity"));
        colStockReorder.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("reorderLevel"));
        colStockUpdated.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getLastUpdated() != null
                        ? c.getValue().getLastUpdated().toString().replace("T", " ") : "—"));
        colStockAlert.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().isBelowReorder() ? "⚠ Low Stock" : "✔ OK"));

        colStockAlert.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); setStyle(""); return; }
                setText(s);
                setStyle(s.contains("Low")
                        ? "-fx-text-fill:#e65100;-fx-font-weight:bold;"
                        : "-fx-text-fill:#27ae60;-fx-font-weight:bold;");
            }
        });

        tblStock.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Stock item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setStyle(""); return; }
                setStyle(item.isBelowReorder() ? "-fx-background-color:#fff3e0;" : "");
            }
        });

        tblStock.setItems(stockData);
    }

    private void setupLogTable() {
        colLogId.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("logId"));
        colLogProduct.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("productName"));
        colLogQty.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("changeQty"));
        colLogType.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getLogType().name()));
        colLogNote.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("note"));
        colLogActor.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("actorName"));
        colLogDate.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getLoggedAt() != null
                        ? c.getValue().getLoggedAt().toString().replace("T", " ") : "—"));

        colLogType.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); setStyle(""); return; }
                setText(s);
                setStyle(switch (s) {
                    case "IN"                    -> "-fx-text-fill:#27ae60;-fx-font-weight:bold;";
                    case "ADJUST"                -> "-fx-text-fill:#e65100;-fx-font-weight:bold;";
                    case "OUT", "CANCEL"         -> "-fx-text-fill:#c62828;-fx-font-weight:bold;";
                    case "WARRANTY_OUT", "RETURN"-> "-fx-text-fill:#6a1b9a;-fx-font-weight:bold;";
                    default                      -> "-fx-text-fill:#37474f;";
                });
            }
        });

        tblLog.setItems(logData);
    }

    private void setupLogFilter() {
        cbLogType.setItems(FXCollections.observableArrayList(
                "", "IN", "OUT", "ADJUST", "CANCEL", "RETURN", "WARRANTY_OUT", "WARRANTY_IN"));
        cbLogType.setValue("");
    }

    // ── Load data ──────────────────────────────────────────────────────────
    private void loadProducts() {
        try {
            ProductService ps = new ProductService();
            ObservableList<Product> list = FXCollections.observableArrayList(ps.getAllProducts());
            cbLogProduct.setItems(list);
        } catch (Exception ignored) {}
    }

    private void loadStock() {
        try {
            List<Stock> list = service.getAllStock();
            stockData.setAll(list);
            long low = list.stream().filter(Stock::isBelowReorder).count();
            setStatus("Loaded " + list.size() + " products"
                    + (low > 0 ? " | ⚠ " + low + " below reorder level." : "."), low > 0);
        } catch (Exception e) { setStatus("Error: " + e.getMessage(), true); }
    }

    private void loadLog() {
        try {
            Product selProd = cbLogProduct.getValue();
            Integer productId = selProd != null ? selProd.getProductId() : null;
            String  logType   = cbLogType.getValue();
            Timestamp from = dpFrom.getValue() != null
                    ? Timestamp.valueOf(dpFrom.getValue().atStartOfDay()) : null;
            Timestamp to   = dpTo.getValue()   != null
                    ? Timestamp.valueOf(dpTo.getValue().atTime(LocalTime.MAX)) : null;
            List<StockLog> logs = service.getMovementLog(productId, logType, from, to);
            logData.setAll(logs);
            setStatus("Showing " + logs.size() + " log entries.", false);
        } catch (Exception e) { setStatus("Error: " + e.getMessage(), true); }
    }

    @FXML public void handleRefreshStock() { loadStock(); loadLog(); }
    @FXML public void handleFilterLog()    { loadLog(); }
    @FXML public void handleClearFilter()  {
        cbLogProduct.setValue(null); cbLogType.setValue("");
        dpFrom.setValue(null); dpTo.setValue(null);
        loadLog();
    }

    // ==================== STOCK IN DIALOG ====================
    @FXML public void handleStockIn() {
        if (actorId <= 0) {
            alert(Alert.AlertType.WARNING, "Not Allowed",
                    "No Employee record linked to this account. Cannot record stock.");
            return;
        }

        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Stock In — Receive Inventory");

        ComboBox<Product> cbProduct = new ComboBox<>();
        TextField         tfQty     = new TextField();
        TextField         tfNote    = new TextField();
        cbProduct.setMaxWidth(Double.MAX_VALUE);
        tfQty.setPromptText("Positive integer, e.g. 50");
        tfNote.setPromptText("Optional — invoice no., supplier, etc.");

        try {
            cbProduct.setItems(FXCollections.observableArrayList(
                    new ProductService().getAllProducts()));
        } catch (Exception ignored) {}

        Label lblErr = new Label();
        lblErr.setStyle("-fx-text-fill:#c62828;-fx-font-size:12px;"); lblErr.setWrapText(true);

        GridPane grid = buildGrid();
        int row = 0;
        grid.add(sec("-- Stock In Details"),      0, row, 2, 1); row++;
        grid.add(fl("Product *"),      0, row); grid.add(cbProduct, 1, row++);
        grid.add(fl("Quantity *"),     0, row); grid.add(tfQty,     1, row++);
        grid.add(new Label(""),        0, row); grid.add(hint("Must be a positive integer."), 1, row++);
        grid.add(fl("Note"),           0, row); grid.add(tfNote,    1, row++);
        grid.add(new Label(""),        0, row); grid.add(hint("Optional. Invoice number, supplier name, etc."), 1, row++);
        grid.add(new Separator(),                 0, row, 2, 1); row++;
        grid.add(lblErr,                          0, row, 2, 1); row++;

        Button btnSave   = new Button("Record Stock In");
        btnSave.setStyle("-fx-background-color:#27ae60;-fx-text-fill:white;-fx-background-radius:6;" +
                         "-fx-padding:8 18;-fx-font-weight:bold;");
        Button btnCancel = new Button("Cancel");
        btnCancel.setStyle("-fx-background-radius:6;-fx-padding:8 18;");
        HBox btns = new HBox(10, btnCancel, btnSave); btns.setAlignment(Pos.CENTER_RIGHT);
        grid.add(btns, 0, row, 2, 1);

        btnSave.setOnAction(ev -> {
            lblErr.setText("");
            if (cbProduct.getValue() == null) { lblErr.setText("Please select a product."); return; }
            try {
                int qty = Integer.parseInt(tfQty.getText().trim());
                if (qty <= 0) throw new NumberFormatException();
                service.stockIn(cbProduct.getValue().getProductId(),
                        qty, tfNote.getText().trim(), actorId);
                setStatus("Stock In recorded: +" + qty + " × " + cbProduct.getValue().getName(), false);
                loadStock(); loadLog(); dlg.close();
            } catch (NumberFormatException ex) { lblErr.setText("Quantity must be a positive integer."); }
              catch (IllegalArgumentException ex) { lblErr.setText(ex.getMessage()); }
              catch (Exception ex) { lblErr.setText("Error: " + ex.getMessage()); }
        });
        btnCancel.setOnAction(ev -> dlg.close());

        dlg.setScene(new Scene(grid, 480, 340));
        dlg.setResizable(false); dlg.showAndWait();
    }

    // ==================== ADJUST STOCK DIALOG ====================
    @FXML public void handleAdjust() {
        if (actorId <= 0) {
            alert(Alert.AlertType.WARNING, "Not Allowed",
                    "No Employee record linked to this account. Cannot record adjustment.");
            return;
        }

        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Adjust Stock — Correction / Audit");

        ComboBox<Product> cbProduct = new ComboBox<>();
        TextField         tfQty     = new TextField();
        TextField         tfNote    = new TextField();
        cbProduct.setMaxWidth(Double.MAX_VALUE);
        tfQty.setPromptText("Positive or negative integer, e.g. -3 or +10");
        tfNote.setPromptText("Required — reason for adjustment");

        try {
            cbProduct.setItems(FXCollections.observableArrayList(
                    new ProductService().getAllProducts()));
        } catch (Exception ignored) {}

        Label lblErr = new Label();
        lblErr.setStyle("-fx-text-fill:#c62828;-fx-font-size:12px;"); lblErr.setWrapText(true);

        GridPane grid = buildGrid();
        int row = 0;
        grid.add(sec("-- Stock Adjustment"),          0, row, 2, 1); row++;
        grid.add(fl("Product *"),          0, row); grid.add(cbProduct, 1, row++);
        grid.add(fl("Change Qty *"),       0, row); grid.add(tfQty,     1, row++);
        grid.add(new Label(""),            0, row); grid.add(hint("Use negative to reduce, positive to increase. Cannot be 0."), 1, row++);
        grid.add(fl("Reason *"),           0, row); grid.add(tfNote,    1, row++);
        grid.add(new Label(""),            0, row); grid.add(hint("Required for audit trail."), 1, row++);
        grid.add(new Separator(),                     0, row, 2, 1); row++;
        grid.add(lblErr,                              0, row, 2, 1); row++;

        Button btnSave   = new Button("Apply Adjustment");
        btnSave.setStyle("-fx-background-color:#e65100;-fx-text-fill:white;-fx-background-radius:6;" +
                         "-fx-padding:8 18;-fx-font-weight:bold;");
        Button btnCancel = new Button("Cancel");
        btnCancel.setStyle("-fx-background-radius:6;-fx-padding:8 18;");
        HBox btns = new HBox(10, btnCancel, btnSave); btns.setAlignment(Pos.CENTER_RIGHT);
        grid.add(btns, 0, row, 2, 1);

        btnSave.setOnAction(ev -> {
            lblErr.setText("");
            if (cbProduct.getValue() == null) { lblErr.setText("Please select a product."); return; }
            if (tfNote.getText().trim().isEmpty()) { lblErr.setText("Reason is required."); return; }
            try {
                int qty = Integer.parseInt(tfQty.getText().trim());
                if (qty == 0) throw new NumberFormatException();
                service.adjustStock(cbProduct.getValue().getProductId(),
                        qty, tfNote.getText().trim(), actorId);
                setStatus("Stock Adjusted: " + (qty >= 0 ? "+" : "") + qty
                        + " × " + cbProduct.getValue().getName(), false);
                loadStock(); loadLog(); dlg.close();
            } catch (NumberFormatException ex) { lblErr.setText("Quantity must be a non-zero integer."); }
              catch (IllegalArgumentException ex) { lblErr.setText(ex.getMessage()); }
              catch (Exception ex) { lblErr.setText("Error: " + ex.getMessage()); }
        });
        btnCancel.setOnAction(ev -> dlg.close());

        dlg.setScene(new Scene(grid, 480, 340));
        dlg.setResizable(false); dlg.showAndWait();
    }


    // ── UI helpers ─────────────────────────────────────────────────────────
    private GridPane buildGrid() {
        GridPane g = new GridPane(); g.setHgap(12); g.setVgap(10); g.setPadding(new Insets(24));
        g.getColumnConstraints().addAll(new ColumnConstraints(145), new ColumnConstraints(290));
        return g;
    }

    private Label fl(String t)   { Label l = new Label(t); l.setStyle("-fx-font-weight:bold;-fx-font-size:12px;"); return l; }
    private Label sec(String t)  { Label l = new Label(t); l.setStyle("-fx-font-weight:bold;-fx-font-size:11px;-fx-text-fill:#3949ab;"); return l; }
    private Label hint(String t) { Label l = new Label(t); l.setStyle("-fx-font-size:10px;-fx-text-fill:#888;"); return l; }
    private void setStatus(String msg, boolean isError) {
        if (lblStatus == null) return;
        lblStatus.setText(msg);
        lblStatus.setStyle(isError ? "-fx-text-fill:#c62828;-fx-font-size:12px;"
                                   : "-fx-text-fill:#2e7d32;-fx-font-size:12px;");
    }
    private void alert(Alert.AlertType t, String title, String msg) {
        Alert a = new Alert(t); a.setTitle(title); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }
}