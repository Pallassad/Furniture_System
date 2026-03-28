package furniture_system.controller;

import furniture_system.model.Product;
import furniture_system.model.Stock;
import furniture_system.service.ProductService;
import furniture_system.service.StockService;
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

import java.util.List;

/**
 * Employee – Inventory Check (READ-ONLY).
 * View stock levels + check availability via Modal Dialog.
 */
public class EmployeeStockController {

    // ── Stock Table ────────────────────────────────────────────────────────
    @FXML private TableView<Stock>            tblStock;
    @FXML private TableColumn<Stock, String>  colProduct;
    @FXML private TableColumn<Stock, Integer> colQty;
    @FXML private TableColumn<Stock, Integer> colReorder;
    @FXML private TableColumn<Stock, String>  colUpdated;
    @FXML private TableColumn<Stock, String>  colAlert;

    // ── Search & Toolbar ───────────────────────────────────────────────────
    @FXML private TextField txtSearch;
    @FXML private Button    btnCheckAvail;
    @FXML private Label     lblStatus;

    private final StockService               service   = new StockService();
    private final ObservableList<Stock>      stockData = FXCollections.observableArrayList();

    // ══════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        setupTable();
        loadStock();

        tblStock.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> btnCheckAvail.setDisable(sel == null));
        btnCheckAvail.setDisable(true);
    }

    // ── Table setup ────────────────────────────────────────────────────────
    private void setupTable() {
        colProduct.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("productName"));
        colQty.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("quantity"));
        colReorder.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("reorderLevel"));
        colUpdated.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getLastUpdated() != null
                        ? c.getValue().getLastUpdated().toString().replace("T", " ") : "—"));
        colAlert.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().isBelowReorder() ? "⚠ Low Stock" : "✔ Available"));

        colAlert.setCellFactory(col -> new TableCell<>() {
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

    // ── Load & Search ──────────────────────────────────────────────────────
    private void loadStock() {
        try {
            List<Stock> list = service.getAllStock();

            // Sort: low-stock items first (ascending by quantity/reorderLevel ratio),
            // then OK items sorted alphabetically by product name.
            list.sort((a, b) -> {
                boolean aLow = a.isBelowReorder();
                boolean bLow = b.isBelowReorder();
                if (aLow != bLow) return aLow ? -1 : 1; // low-stock floats to top
                if (aLow) {
                    // Both low: the one with less remaining stock comes first
                    int diff = a.getQuantity() - b.getQuantity();
                    if (diff != 0) return diff;
                }
                // Alphabetical fallback
                String na = a.getProductName() != null ? a.getProductName() : "";
                String nb = b.getProductName() != null ? b.getProductName() : "";
                return na.compareToIgnoreCase(nb);
            });

            stockData.setAll(list);
            long low = list.stream().filter(Stock::isBelowReorder).count();
            setStatus("Loaded " + list.size() + " products"
                    + (low > 0 ? " | ⚠ " + low + " below reorder level." : "."), low > 0);
        } catch (Exception e) { setStatus("Error: " + e.getMessage(), true); }
    }

    @FXML public void handleRefresh() { txtSearch.clear(); loadStock(); }

    @FXML public void handleSearch() {
        String kw = txtSearch.getText().trim().toLowerCase();
        if (kw.isBlank()) { loadStock(); return; }
        // Filter then re-apply sort so low-stock items stay on top
        List<Stock> filtered = stockData.stream()
                .filter(s -> s.getProductName() != null
                        && s.getProductName().toLowerCase().contains(kw))
                .sorted((a, b) -> {
                    boolean aLow = a.isBelowReorder();
                    boolean bLow = b.isBelowReorder();
                    if (aLow != bLow) return aLow ? -1 : 1;
                    if (aLow) {
                        int diff = a.getQuantity() - b.getQuantity();
                        if (diff != 0) return diff;
                    }
                    String na = a.getProductName() != null ? a.getProductName() : "";
                    String nb = b.getProductName() != null ? b.getProductName() : "";
                    return na.compareToIgnoreCase(nb);
                })
                .toList();
        tblStock.setItems(FXCollections.observableArrayList(filtered));
        setStatus("Found " + filtered.size() + " product(s).", false);
    }

    // ==================== CHECK AVAILABILITY DIALOG ====================
    @FXML public void handleCheckAvailability() {
        Stock sel = tblStock.getSelectionModel().getSelectedItem();
        if (sel != null) openCheckDialog(sel);
    }

    private void openCheckDialog(Stock stock) {
        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Check Availability - " + stock.getProductName());

        TextField tfRequired = new TextField();
        tfRequired.setPromptText("e.g. 5");

        Label lblResult = new Label();
        lblResult.setWrapText(true);
        lblResult.setStyle("-fx-font-size:13px;-fx-padding:10 14;-fx-background-radius:6;");

        GridPane grid = buildGrid();
        int row = 0;

        Label lblProdName = new Label(stock.getProductName());
        lblProdName.setStyle("-fx-font-size:15px;-fx-font-weight:bold;-fx-text-fill:#1a237e;");
        grid.add(lblProdName,                  0, row, 2, 1); row++;
        grid.add(new Separator(),              0, row, 2, 1); row++;

        grid.add(sec("-- Current Stock"),      0, row, 2, 1); row++;
        Label lblInStock = new Label(String.valueOf(stock.getQuantity()));
        lblInStock.setStyle("-fx-font-size:16px;-fx-font-weight:bold;"
                + (stock.isBelowReorder() ? "-fx-text-fill:#e65100;" : "-fx-text-fill:#27ae60;"));
        grid.add(fl("In Stock"),    0, row); grid.add(lblInStock,                         1, row++);
        grid.add(fl("Reorder Lvl"),0, row); grid.add(val(String.valueOf(stock.getReorderLevel())), 1, row++);

        String alertText = stock.isBelowReorder() ? "⚠ Below reorder level" : "✔ OK";
        Label lblAlert = new Label(alertText);
        lblAlert.setStyle("-fx-font-weight:bold;" +
                (stock.isBelowReorder() ? "-fx-text-fill:#e65100;" : "-fx-text-fill:#27ae60;"));
        grid.add(fl("Status"),      0, row); grid.add(lblAlert,                           1, row++);
        grid.add(new Separator(),              0, row, 2, 1); row++;

        grid.add(sec("-- Availability Check"), 0, row, 2, 1); row++;
        grid.add(fl("Required Qty"), 0, row); grid.add(tfRequired, 1, row++);
        grid.add(new Label(""),      0, row); grid.add(hint("Enter the quantity needed for your order."), 1, row++);
        grid.add(lblResult,                    0, row, 2, 1); row++;
        grid.add(new Separator(),              0, row, 2, 1); row++;

        Button btnCheck  = primaryBtn("Check");
        Button btnClose  = new Button("Close");
        btnClose.setStyle("-fx-background-radius:6;-fx-padding:8 18;");
        HBox btns = new HBox(10, btnClose, btnCheck); btns.setAlignment(Pos.CENTER_RIGHT);
        grid.add(btns, 0, row, 2, 1);

        btnCheck.setOnAction(ev -> {
            lblResult.setText("");
            try {
                int required = Integer.parseInt(tfRequired.getText().trim());
                if (required <= 0) throw new NumberFormatException();
                if (stock.getQuantity() >= required) {
                    lblResult.setText("✔ Available! In stock: " + stock.getQuantity()
                            + "  |  Requested: " + required
                            + "  |  Remaining after: " + (stock.getQuantity() - required));
                    lblResult.setStyle("-fx-font-size:13px;-fx-font-weight:bold;" +
                            "-fx-text-fill:#27ae60;-fx-background-color:#e8f5e9;" +
                            "-fx-padding:10 14;-fx-background-radius:6;");
                } else {
                    lblResult.setText("✘ Insufficient stock! In stock: " + stock.getQuantity()
                            + "  |  Requested: " + required
                            + "  |  Shortage: " + (required - stock.getQuantity()));
                    lblResult.setStyle("-fx-font-size:13px;-fx-font-weight:bold;" +
                            "-fx-text-fill:#c62828;-fx-background-color:#ffebee;" +
                            "-fx-padding:10 14;-fx-background-radius:6;");
                }
            } catch (NumberFormatException ex) {
                lblResult.setText("⚠ Please enter a valid positive integer.");
                lblResult.setStyle("-fx-font-size:12px;-fx-text-fill:#e65100;");
            }
        });
        btnClose.setOnAction(ev -> dlg.close());

        dlg.setScene(new Scene(grid, 480, 460));
        dlg.setResizable(false); dlg.showAndWait();
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private GridPane buildGrid() {
        GridPane g = new GridPane(); g.setHgap(12); g.setVgap(10); g.setPadding(new Insets(24));
        g.getColumnConstraints().addAll(new ColumnConstraints(130), new ColumnConstraints(290));
        return g;
    }

    private Label fl(String t)  { Label l = new Label(t); l.setStyle("-fx-font-weight:bold;-fx-font-size:12px;"); return l; }
    private Label sec(String t) { Label l = new Label(t); l.setStyle("-fx-font-weight:bold;-fx-font-size:11px;-fx-text-fill:#3949ab;"); return l; }
    private Label val(String t) { Label l = new Label(t); l.setStyle("-fx-font-size:12px;"); l.setWrapText(true); return l; }
    private Label hint(String t){ Label l = new Label(t); l.setStyle("-fx-font-size:10px;-fx-text-fill:#888;"); return l; }
    private Button primaryBtn(String t) {
        Button b = new Button(t);
        b.setStyle("-fx-background-color:#3949ab;-fx-text-fill:white;-fx-background-radius:6;-fx-padding:8 18;-fx-font-weight:bold;");
        return b;
    }
    private void setStatus(String msg, boolean isError) {
        if (lblStatus == null) return;
        lblStatus.setText(msg);
        lblStatus.setStyle(isError ? "-fx-text-fill:#c62828;-fx-font-size:12px;"
                                   : (msg.startsWith("✔") || msg.contains("added") || msg.contains("updated")
                || msg.contains("deleted") || msg.contains("created") || msg.contains("saved")
                || msg.contains("recorded") || msg.contains("Adjusted") || msg.contains("linked")
                || msg.contains("success") || msg.contains("Ticket") && msg.contains("→")
                ? "-fx-text-fill:#1e7e4a;-fx-font-weight:bold;-fx-font-size:12px;"
                : "-fx-text-fill:#37474f;-fx-font-size:12px;"));
    }
}