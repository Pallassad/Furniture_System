package furniture_system.controller;

import furniture_system.model.Stock;
import furniture_system.model.StockLog;
import furniture_system.model.StockLog.LogType;
import furniture_system.service.StockService;
import furniture_system.utils.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import furniture_system.config.DatabaseConfig;
import java.sql.*;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * StockController - Admin Inventory / Stock Management.
 *
 * actorId resolution:
 *   StockLog.ActorId is FK -> Employee.EmployeeId.
 *   This controller looks up the Employee row linked to the current Account
 *   via the Account.AccountId -> Employee.AccountId relationship.
 *   If no Employee row exists for the logged-in admin, write operations
 *   (Stock In, Adjust) are disabled with a warning.
 */
public class StockController {

    @FXML private TableView<Stock>              tblStock;
    @FXML private TableColumn<Stock, String>    colStockProduct;
    @FXML private TableColumn<Stock, Integer>   colStockQty;
    @FXML private TableColumn<Stock, Integer>   colStockReorder;
    @FXML private TableColumn<Stock, String>    colStockUpdated;
    @FXML private TableColumn<Stock, String>    colStockAlert;

    @FXML private ComboBox<furniture_system.model.Product> cbInProduct;
    @FXML private TextField txInQty;
    @FXML private TextField txInNote;
    @FXML private Label     lblInError;

    @FXML private ComboBox<furniture_system.model.Product> cbAdjProduct;
    @FXML private TextField txAdjQty;
    @FXML private TextField txAdjNote;
    @FXML private Label     lblAdjError;

    @FXML private TableView<StockLog>              tblLog;
    @FXML private TableColumn<StockLog, Integer>   colLogId;
    @FXML private TableColumn<StockLog, String>    colLogProduct;
    @FXML private TableColumn<StockLog, Integer>   colLogQty;
    @FXML private TableColumn<StockLog, String>    colLogType;
    @FXML private TableColumn<StockLog, String>    colLogNote;
    @FXML private TableColumn<StockLog, String>    colLogActor;
    @FXML private TableColumn<StockLog, String>    colLogDate;
    @FXML private TableColumn<StockLog, Void>      colLogActions;

    @FXML private ComboBox<String>   cbLogType;
    @FXML private DatePicker         dpFrom;
    @FXML private DatePicker         dpTo;
    @FXML private ComboBox<furniture_system.model.Product> cbLogProduct;

    @FXML private Label lblStatus;

    private final StockService service = new StockService();

    /**
     * actorId = Employee.EmployeeId of the logged-in user.
     * Resolved during initialize() by querying Employee WHERE AccountId = currentAccountId.
     * -1 means no Employee row found; write operations will be blocked.
     */
    private int actorId = -1;

    @FXML
    public void initialize() {
        resolveActorId();
        setupStockTable();
        setupLogTable();

        cbLogType.setItems(FXCollections.observableArrayList(
            "", "IN", "OUT", "ADJUST", "CANCEL", "RETURN", "WARRANTY_OUT", "WARRANTY_IN"));
        cbLogType.setValue("");

        loadProducts();
        loadStock();
        loadLog();

        // Warn if actor cannot be resolved (admin with no Employee row)
        if (actorId <= 0) {
            setStatus("Warning: No Employee record linked to this account. Stock In/Adjust disabled.", true);
            disableWriteControls();
        }
    }

    /**
     * Resolve Employee.EmployeeId for the current logged-in Account.
     * Schema: Employee.AccountId (UNIQUE FK -> Account.AccountId)
     */
    private void resolveActorId() {
        if (SessionManager.getInstance().getCurrentAccount() == null) return;
        int accountId = SessionManager.getInstance().getCurrentAccount().getAccountId();
        try {
            
            try (Connection con = DatabaseConfig.getConnection();
                 PreparedStatement ps = con.prepareStatement(
                     "SELECT EmployeeId FROM Employee WHERE AccountId = ?")) {
                ps.setInt(1, accountId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) actorId = rs.getInt("EmployeeId");
                }
            }
        } catch (Exception e) {
            actorId = -1;
        }
    }

    private void disableWriteControls() {
        cbInProduct.setDisable(true);  txInQty.setDisable(true);
        txInNote.setDisable(true);
        cbAdjProduct.setDisable(true); txAdjQty.setDisable(true);
        txAdjNote.setDisable(true);
    }

    private void setupStockTable() {
        colStockProduct.setCellValueFactory(new PropertyValueFactory<>("productName"));
        colStockQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colStockReorder.setCellValueFactory(new PropertyValueFactory<>("reorderLevel"));
        colStockUpdated.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getLastUpdated() != null
                ? c.getValue().getLastUpdated().toString().replace("T", " ") : "-"));
        colStockAlert.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().isBelowReorder() ? "Low Stock" : "OK"));

        tblStock.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Stock item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setStyle(""); return; }
                setStyle(item.isBelowReorder() ? "-fx-background-color:#fff3e0;" : "");
            }
        });
    }

    private void setupLogTable() {
        colLogId.setCellValueFactory(new PropertyValueFactory<>("logId"));
        colLogProduct.setCellValueFactory(new PropertyValueFactory<>("productName"));
        colLogQty.setCellValueFactory(new PropertyValueFactory<>("changeQty"));
        colLogType.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getLogType().name()));
        colLogNote.setCellValueFactory(new PropertyValueFactory<>("note"));
        colLogActor.setCellValueFactory(new PropertyValueFactory<>("actorName"));
        // Maps to DB column CreatedAt (via getLoggedAt())
        colLogDate.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getLoggedAt() != null
                ? c.getValue().getLoggedAt().toString().replace("T", " ") : "-"));

        colLogType.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                switch (item) {
                    case "IN"     -> setStyle("-fx-text-fill:#2e7d32; -fx-font-weight:bold;");
                    case "ADJUST" -> setStyle("-fx-text-fill:#e65100; -fx-font-weight:bold;");
                    case "OUT", "CANCEL" -> setStyle("-fx-text-fill:#c62828; -fx-font-weight:bold;");
                    default       -> setStyle("-fx-text-fill:#37474f;");
                }
            }
        });

        colLogActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnDel = new Button("Delete");
            {
                btnDel.setStyle("-fx-background-color:#c62828;-fx-text-fill:white;" +
                                "-fx-background-radius:4;-fx-cursor:hand;-fx-font-size:11px;");
                btnDel.setOnAction(e -> handleDeleteLog(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                StockLog log = getTableView().getItems().get(getIndex());
                boolean deletable = log != null &&
                    (log.getLogType() == LogType.IN || log.getLogType() == LogType.ADJUST);
                setGraphic(deletable ? btnDel : null);
            }
        });
    }

    private void loadProducts() {
        try {
            furniture_system.service.ProductService ps = new furniture_system.service.ProductService();
            var products = FXCollections.observableArrayList(ps.getAllProducts());
            cbInProduct.setItems(products);
            cbAdjProduct.setItems(products);
            cbLogProduct.setItems(FXCollections.observableArrayList(ps.getAllProducts()));
        } catch (Exception ignored) {}
    }

    @FXML public void handleRefreshStock() { loadStock(); }

    private void loadStock() {
        try {
            List<Stock> list = service.getAllStock();
            tblStock.setItems(FXCollections.observableArrayList(list));
            long low = list.stream().filter(Stock::isBelowReorder).count();
            setStatus("Loaded " + list.size() + " products"
                + (low > 0 ? " | " + low + " below reorder level." : "."), low > 0);
        } catch (Exception e) { setStatus("Error: " + e.getMessage(), true); }
    }

    @FXML public void handleFilterLog() { loadLog(); }

    private void loadLog() {
        try {
            furniture_system.model.Product selProd = cbLogProduct.getValue();
            Integer productId = selProd != null ? selProd.getProductId() : null;
            String  logType   = cbLogType.getValue();
            Timestamp from = dpFrom.getValue() != null
                ? Timestamp.valueOf(dpFrom.getValue().atStartOfDay()) : null;
            Timestamp to   = dpTo.getValue()   != null
                ? Timestamp.valueOf(dpTo.getValue().atTime(LocalTime.MAX)) : null;
            List<StockLog> logs = service.getMovementLog(productId, logType, from, to);
            tblLog.setItems(FXCollections.observableArrayList(logs));
            setStatus("Showing " + logs.size() + " log entries.", false);
        } catch (Exception e) { setStatus("Error: " + e.getMessage(), true); }
    }

    @FXML public void handleStockIn() {
        lblInError.setText("");
        if (actorId <= 0) { lblInError.setText("No Employee record linked - cannot record stock."); return; }
        furniture_system.model.Product p = cbInProduct.getValue();
        if (p == null) { lblInError.setText("Select a product."); return; }
        try {
            int qty = Integer.parseInt(txInQty.getText().trim());
            service.stockIn(p.getProductId(), qty, txInNote.getText().trim(), actorId);
            setStatus("Stock In recorded for " + p.getName() + " (+" + qty + ").", false);
            txInQty.clear(); txInNote.clear();
            loadStock(); loadLog();
        } catch (NumberFormatException ex) {
            lblInError.setText("Quantity must be an integer.");
        } catch (IllegalArgumentException ex) {
            lblInError.setText(ex.getMessage());
        } catch (Exception ex) {
            lblInError.setText("Error: " + ex.getMessage());
        }
    }

    @FXML public void handleAdjust() {
        lblAdjError.setText("");
        if (actorId <= 0) { lblAdjError.setText("No Employee record linked - cannot record adjustment."); return; }
        furniture_system.model.Product p = cbAdjProduct.getValue();
        if (p == null) { lblAdjError.setText("Select a product."); return; }
        try {
            int qty = Integer.parseInt(txAdjQty.getText().trim());
            service.adjustStock(p.getProductId(), qty, txAdjNote.getText().trim(), actorId);
            setStatus("Stock Adjusted for " + p.getName() + " (" + (qty >= 0 ? "+" : "") + qty + ").", false);
            txAdjQty.clear(); txAdjNote.clear();
            loadStock(); loadLog();
        } catch (NumberFormatException ex) {
            lblAdjError.setText("Quantity must be an integer (can be negative).");
        } catch (IllegalArgumentException ex) {
            lblAdjError.setText(ex.getMessage());
        } catch (Exception ex) {
            lblAdjError.setText("Error: " + ex.getMessage());
        }
    }

    private void handleDeleteLog(StockLog log) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Delete log #" + log.getLogId() + "?\n" +
            "Stock change (" + (log.getChangeQty() >= 0 ? "+" : "") + log.getChangeQty() +
            ") will be reversed.\nThis is irreversible.",
            ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Delete");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            try {
                service.deleteLog(log.getLogId(), log.getLogType());
                setStatus("Log entry deleted and stock reversed.", false);
                loadStock(); loadLog();
            } catch (IllegalArgumentException ex) {
                setStatus("Not allowed: " + ex.getMessage(), true);
            } catch (Exception ex) {
                setStatus("Error: " + ex.getMessage(), true);
            }
        }
    }

    private void setStatus(String msg, boolean isError) {
        if (lblStatus == null) return;
        lblStatus.setText(msg);
        lblStatus.setStyle(isError
            ? "-fx-text-fill:#c62828; -fx-font-size:12px;"
            : "-fx-text-fill:#2e7d32; -fx-font-size:12px;");
    }
}
