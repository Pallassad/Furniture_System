package furniture_system.controller;

import furniture_system.model.Promotion;
import furniture_system.service.PromotionService;
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

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Employee – Promotion View (READ-ONLY)
 * Fullwidth table + Modal Dialog pattern (same as AuthController).
 * Employee can VIEW active promotions and LOOKUP a code.
 */
public class EmployeePromotionController {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ── Table ──────────────────────────────────────────────────────────────
    @FXML private TableView<Promotion>            promoTable;
    @FXML private TableColumn<Promotion, String>  colCode;
    @FXML private TableColumn<Promotion, String>  colName;
    @FXML private TableColumn<Promotion, String>  colType;
    @FXML private TableColumn<Promotion, String>  colValue;
    @FXML private TableColumn<Promotion, String>  colMin;
    @FXML private TableColumn<Promotion, String>  colDates;
    @FXML private TableColumn<Promotion, String>  colUsage;

    // ── Search & Toolbar ───────────────────────────────────────────────────
    @FXML private TextField txtSearch;
    @FXML private Button    btnViewDetail;
    @FXML private Label     statusBarLabel;

    private final PromotionService          svc  = new PromotionService();
    private final ObservableList<Promotion> data = FXCollections.observableArrayList();

    // ══════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        setupColumns();
        loadActivePromotions();

        promoTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> btnViewDetail.setDisable(sel == null));
        btnViewDetail.setDisable(true);

        // Double-click → view detail
        promoTable.setRowFactory(tv -> {
            TableRow<Promotion> row = new TableRow<>();
            row.setOnMouseClicked(e -> { if (e.getClickCount() == 2 && !row.isEmpty()) openDetailDialog(row.getItem()); });
            return row;
        });
    }

    // ── Column setup ───────────────────────────────────────────────────────
    private void setupColumns() {
        colCode.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCode()));
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        colType.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDiscountType()));
        colValue.setCellValueFactory(c -> {
            Promotion p = c.getValue();
            return new SimpleStringProperty("PERCENT".equals(p.getDiscountType())
                    ? String.format("%.0f%%", p.getDiscountValue())
                    : String.format("%,.0f ₫", p.getDiscountValue()));
        });
        colMin.setCellValueFactory(c -> {
            Promotion p = c.getValue();
            return new SimpleStringProperty(p.getMinOrderValue() != null
                    && p.getMinOrderValue().compareTo(BigDecimal.ZERO) > 0
                    ? String.format("%,.0f ₫", p.getMinOrderValue()) : "—");
        });
        colDates.setCellValueFactory(c -> {
            Promotion p = c.getValue();
            String s = p.getStartDate() != null ? p.getStartDate().format(DTF) : "?";
            String e = p.getEndDate()   != null ? p.getEndDate().format(DTF)   : "?";
            return new SimpleStringProperty(s + " → " + e);
        });
        colUsage.setCellValueFactory(c -> {
            Promotion p = c.getValue();
            String limit = p.getUsageLimit() != null ? String.valueOf(p.getUsageLimit()) : "∞";
            return new SimpleStringProperty(p.getUsedCount() + " / " + limit);
        });

        promoTable.setItems(data);
    }

    // ── Load ───────────────────────────────────────────────────────────────
    private void loadActivePromotions() {
        try {
            List<Promotion> list = svc.getActivePromotions();
            data.setAll(list);
            setStatus("Showing " + list.size() + " active promotion(s).");
        } catch (Exception e) { alert(Alert.AlertType.ERROR, "Error", e.getMessage()); }
    }

    @FXML public void handleRefresh() {
        txtSearch.clear();
        loadActivePromotions();
    }

    @FXML public void handleSearch() {
        String kw = txtSearch.getText().trim().toLowerCase();
        if (kw.isBlank()) { loadActivePromotions(); return; }
        ObservableList<Promotion> filtered = FXCollections.observableArrayList(
                data.filtered(p ->
                        p.getCode().toLowerCase().contains(kw) ||
                        p.getName().toLowerCase().contains(kw)));
        promoTable.setItems(filtered);
        setStatus("Found " + filtered.size() + " promotion(s).");
    }


    // ==================== VIEW DETAIL DIALOG ====================
    @FXML public void handleViewDetail() {
        Promotion sel = promoTable.getSelectionModel().getSelectedItem();
        if (sel != null) openDetailDialog(sel);
    }

    private void openDetailDialog(Promotion p) {
        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Promotion Detail - " + p.getCode());

        GridPane grid = new GridPane();
        grid.setHgap(16); grid.setVgap(12); grid.setPadding(new Insets(28));
        grid.getColumnConstraints().addAll(new ColumnConstraints(140), new ColumnConstraints(300));

        int row = 0;

        // Header
        Label lblCode = new Label(p.getCode());
        lblCode.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:#1a237e;");
        Label lblName = new Label(p.getName());
        lblName.setStyle("-fx-font-size:13px;-fx-text-fill:#555;"); lblName.setWrapText(true);
        grid.add(lblCode,                      0, row, 2, 1); row++;
        grid.add(lblName,                      0, row, 2, 1); row++;
        grid.add(new Separator(),              0, row, 2, 1); row++;

        // Discount info
        grid.add(sec("-- Discount"),           0, row, 2, 1); row++;

        String discStr = "PERCENT".equals(p.getDiscountType())
                ? String.format("%.0f%% off", p.getDiscountValue())
                : String.format("%,.0f ₫ off", p.getDiscountValue());
        Label lblDisc = new Label(discStr);
        lblDisc.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-text-fill:#27ae60;");
        grid.add(fl("Discount"),    0, row); grid.add(lblDisc,                                1, row++);
        grid.add(fl("Type"),        0, row); grid.add(val(p.getDiscountType()),               1, row++);

        String minStr = p.getMinOrderValue() != null && p.getMinOrderValue().compareTo(BigDecimal.ZERO) > 0
                ? String.format("%,.0f ₫", p.getMinOrderValue()) : "No minimum";
        grid.add(fl("Min Order"),   0, row); grid.add(val(minStr),                           1, row++);

        String limitStr = p.getUsageLimit() != null
                ? p.getUsedCount() + " used / " + p.getUsageLimit() + " limit"
                : p.getUsedCount() + " used / ∞ limit";
        grid.add(fl("Usage"),       0, row); grid.add(val(limitStr),                         1, row++);
        grid.add(new Separator(),              0, row, 2, 1); row++;

        // Period
        grid.add(sec("-- Valid Period"),       0, row, 2, 1); row++;
        String start = p.getStartDate() != null ? p.getStartDate().format(DTF) : "?";
        String end   = p.getEndDate()   != null ? p.getEndDate().format(DTF)   : "?";
        grid.add(fl("Start Date"),  0, row); grid.add(val(start),                            1, row++);
        grid.add(fl("End Date"),    0, row); grid.add(val(end),                              1, row++);
        grid.add(new Separator(),              0, row, 2, 1); row++;

        // Status
        Label lblStatus = new Label("● " + p.getStatus());
        lblStatus.setStyle("-fx-font-weight:bold;-fx-font-size:12px;" +
                ("ACTIVE".equals(p.getStatus()) ? "-fx-text-fill:#27ae60;" : "-fx-text-fill:#1565c0;"));
        grid.add(fl("Status"),      0, row); grid.add(lblStatus,                             1, row++);
        grid.add(new Separator(),              0, row, 2, 1); row++;

        // How to use hint
        Label lblHint = new Label("💡 To apply this promotion, select code \""
                + p.getCode() + "\" in the Promotion field when creating a new order.");
        lblHint.setStyle("-fx-font-size:11px;-fx-text-fill:#888;-fx-wrap-text:true;");
        lblHint.setWrapText(true);
        grid.add(lblHint, 0, row, 2, 1); row++;
        grid.add(new Separator(), 0, row, 2, 1); row++;

        Button btnClose = primaryBtn("Close");
        btnClose.setOnAction(ev -> dlg.close());
        HBox btns = new HBox(btnClose); btns.setAlignment(Pos.CENTER_RIGHT);
        grid.add(btns, 0, row, 2, 1);

        ScrollPane scroll = new ScrollPane(grid); scroll.setFitToWidth(true);
        dlg.setScene(new Scene(scroll, 500, 520));
        dlg.setResizable(false); dlg.showAndWait();
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private Label fl(String t)  { Label l = new Label(t); l.setStyle("-fx-font-weight:bold;-fx-font-size:12px;"); return l; }
    private Label sec(String t) { Label l = new Label(t); l.setStyle("-fx-font-weight:bold;-fx-font-size:11px;-fx-text-fill:#3949ab;"); return l; }
    private Label val(String t) { Label l = new Label(t); l.setStyle("-fx-font-size:12px;"); l.setWrapText(true); return l; }
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
}