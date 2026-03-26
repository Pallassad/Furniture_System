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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Admin – Promotion Management
 * Fullwidth table + Modal Dialog pattern (same as AuthController).
 */
public class AdminPromotionController {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ── Table ───���──────────────────────────────────────────────────────────
    @FXML private TableView<Promotion>            promoTable;
    @FXML private TableColumn<Promotion, Integer> colId;
    @FXML private TableColumn<Promotion, String>  colCode;
    @FXML private TableColumn<Promotion, String>  colName;
    @FXML private TableColumn<Promotion, String>  colType;
    @FXML private TableColumn<Promotion, String>  colValue;
    @FXML private TableColumn<Promotion, String>  colDates;
    @FXML private TableColumn<Promotion, String>  colUsage;
    @FXML private TableColumn<Promotion, String>  colStatus;

    // ── Search ─────────────────────────────────────────────────────────────
    @FXML private TextField        txtSearch;
    @FXML private ComboBox<String> cmbSearchStatus;
    @FXML private DatePicker       dpSearchFrom;
    @FXML private DatePicker       dpSearchTo;

    // ── Toolbar ────────────────────────────────────────────────────────────
    @FXML private Button btnAdd;
    @FXML private Button btnEdit;
    @FXML private Button btnDelete;
    @FXML private Label  statusBarLabel;

    private final PromotionService           svc  = new PromotionService();
    private final ObservableList<Promotion>  data = FXCollections.observableArrayList();

    // ══════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        setupColumns();
        setupSearchCombo();
        loadData();

        promoTable.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            boolean has = sel != null;
            btnEdit.setDisable(!has);
            btnDelete.setDisable(!has);
        });
        btnEdit.setDisable(true);
        btnDelete.setDisable(true);
    }

    // ── Column setup ───────────────────────────────────────────────────────
    private void setupColumns() {
        colId.setCellValueFactory(c ->
                new javafx.beans.property.SimpleIntegerProperty(c.getValue().getPromoId()).asObject());
        colCode.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getCode()));
        colName.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getName()));
        colType.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getDiscountType()));
        colValue.setCellValueFactory(c -> {
            Promotion p = c.getValue();
            String val = "PERCENT".equals(p.getDiscountType())
                    ? String.format("%.0f%%", p.getDiscountValue())
                    : String.format("%,.0f ₫", p.getDiscountValue());
            return new SimpleStringProperty(val);
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
        colStatus.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getStatus()));

        // Status color
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); setStyle(""); return; }
                setText(s);
                setStyle(switch (s) {
                    case "ACTIVE"   -> "-fx-text-fill:#27ae60;-fx-font-weight:bold;";
                    case "UPCOMING" -> "-fx-text-fill:#1565c0;-fx-font-weight:bold;";
                    case "EXPIRED"  -> "-fx-text-fill:#e65100;-fx-font-weight:bold;";
                    case "DISABLED" -> "-fx-text-fill:#c62828;-fx-font-weight:bold;";
                    default         -> "";
                });
            }
        });

        // Row color for DISABLED / EXPIRED
        promoTable.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Promotion item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setStyle(""); return; }
                setStyle(switch (item.getStatus()) {
                    case "DISABLED" -> "-fx-background-color:#e8e8e8;";
                    case "EXPIRED"  -> "-fx-background-color:#fff3e0;";
                    default         -> "";
                });
            }
        });

        promoTable.setItems(data);
    }

    private void setupSearchCombo() {
        cmbSearchStatus.setItems(FXCollections.observableArrayList(
                "", "UPCOMING", "ACTIVE", "EXPIRED", "DISABLED"));
    }

    // ── Load & Search ──────────────────────────────────────────────────────
    private void loadData() {
        try {
            List<Promotion> list = svc.getAll();
            data.setAll(list);
            setStatus("Loaded " + list.size() + " promotion(s).");
        } catch (Exception e) { alert(Alert.AlertType.ERROR, "Error", e.getMessage()); }
    }

    @FXML public void handleRefresh() {
        txtSearch.clear(); cmbSearchStatus.setValue("");
        dpSearchFrom.setValue(null); dpSearchTo.setValue(null);
        loadData();
    }

    @FXML public void handleSearch() {
        try {
            String keyword = txtSearch.getText();
            String status  = cmbSearchStatus.getValue();
            LocalDateTime from = dpSearchFrom.getValue() != null
                    ? dpSearchFrom.getValue().atStartOfDay() : null;
            LocalDateTime to   = dpSearchTo.getValue() != null
                    ? dpSearchTo.getValue().atTime(23, 59, 59) : null;
            List<Promotion> list = svc.search(keyword, status, from, to);
            data.setAll(list);
            setStatus("Found " + list.size() + " promotion(s).");
        } catch (Exception e) { alert(Alert.AlertType.ERROR, "Search Error", e.getMessage()); }
    }

    // ==================== ADD ====================
    @FXML public void handleAdd() {
        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Add New Promotion");

        TextField        tfCode     = new TextField();
        TextField        tfName     = new TextField();
        ComboBox<String> cbType     = new ComboBox<>(FXCollections.observableArrayList("PERCENT", "FIXED"));
        TextField        tfValue    = new TextField();
        DatePicker       dpStart    = new DatePicker();
        DatePicker       dpEnd      = new DatePicker();
        TextField        tfMinOrder = new TextField();
        TextField        tfLimit    = new TextField();
        ComboBox<String> cbStatus   = new ComboBox<>(FXCollections.observableArrayList(
                "UPCOMING", "ACTIVE", "EXPIRED", "DISABLED"));

        cbType.setValue("PERCENT"); cbStatus.setValue("UPCOMING");
        cbType.setMaxWidth(Double.MAX_VALUE); cbStatus.setMaxWidth(Double.MAX_VALUE);
        dpStart.setMaxWidth(Double.MAX_VALUE); dpEnd.setMaxWidth(Double.MAX_VALUE);
        tfMinOrder.setPromptText("0 = no minimum"); tfLimit.setPromptText("blank = ∞");

        Label lblErr = new Label();
        lblErr.setStyle("-fx-text-fill:#c62828;-fx-font-size:12px;"); lblErr.setWrapText(true);

        GridPane grid = buildGrid();
        int row = 0;
        grid.add(sec("-- Basic Info"),          0, row, 2, 1); row++;
        grid.add(fl("Code *"),       0, row); grid.add(tfCode,     1, row++);
        grid.add(new Label(""),      0, row); grid.add(hint("Unique code, e.g. SUMMER20"), 1, row++);
        grid.add(fl("Name *"),       0, row); grid.add(tfName,     1, row++);
        grid.add(fl("Status *"),     0, row); grid.add(cbStatus,   1, row++);
        grid.add(new Separator(),               0, row, 2, 1); row++;
        grid.add(sec("-- Discount"),            0, row, 2, 1); row++;
        grid.add(fl("Type *"),       0, row); grid.add(cbType,     1, row++);
        grid.add(fl("Value *"),      0, row); grid.add(tfValue,    1, row++);
        grid.add(new Label(""),      0, row); grid.add(hint("PERCENT: 10 = 10%  |  FIXED: 200000 = 200,000 ₫"), 1, row++);
        grid.add(fl("Min Order (₫)"),0, row); grid.add(tfMinOrder, 1, row++);
        grid.add(fl("Usage Limit"),  0, row); grid.add(tfLimit,    1, row++);
        grid.add(new Separator(),               0, row, 2, 1); row++;
        grid.add(sec("-- Period"),              0, row, 2, 1); row++;
        grid.add(fl("Start Date *"), 0, row); grid.add(dpStart,    1, row++);
        grid.add(fl("End Date *"),   0, row); grid.add(dpEnd,      1, row++);
        grid.add(new Separator(),               0, row, 2, 1); row++;
        grid.add(lblErr,                        0, row, 2, 1); row++;

        Button btnSave   = primaryBtn("Add Promotion");
        Button btnCancel = new Button("Cancel");
        btnCancel.setStyle("-fx-background-radius:6;-fx-padding:8 18;");
        HBox btns = new HBox(10, btnCancel, btnSave); btns.setAlignment(Pos.CENTER_RIGHT);
        grid.add(btns, 0, row, 2, 1);

        btnSave.setOnAction(ev -> {
            lblErr.setText("");
            Promotion p = buildPromotion(tfCode, tfName, cbType, tfValue,
                    dpStart, dpEnd, tfMinOrder, tfLimit, cbStatus, lblErr);
            if (p == null) return;
            try {
                svc.addPromotion(p);
                setStatus("Promotion [" + p.getCode() + "] added.");
                loadData(); dlg.close();
            } catch (Exception ex) { lblErr.setText(ex.getMessage()); }
        });
        btnCancel.setOnAction(ev -> dlg.close());

        ScrollPane scroll = new ScrollPane(grid); scroll.setFitToWidth(true);
        dlg.setScene(new Scene(scroll, 500, 600)); dlg.showAndWait();
    }

    // ==================== EDIT ====================
    @FXML public void handleEdit() {
        Promotion sel = promoTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Edit Promotion - " + sel.getCode());

        TextField        tfCode     = new TextField(sel.getCode());
        TextField        tfName     = new TextField(sel.getName());
        ComboBox<String> cbType     = new ComboBox<>(FXCollections.observableArrayList("PERCENT", "FIXED"));
        TextField        tfValue    = new TextField(sel.getDiscountValue() != null ? sel.getDiscountValue().toPlainString() : "");
        DatePicker       dpStart    = new DatePicker(sel.getStartDate() != null ? sel.getStartDate().toLocalDate() : null);
        DatePicker       dpEnd      = new DatePicker(sel.getEndDate()   != null ? sel.getEndDate().toLocalDate()   : null);
        TextField        tfMinOrder = new TextField(sel.getMinOrderValue() != null ? sel.getMinOrderValue().toPlainString() : "0");
        TextField        tfLimit    = new TextField(sel.getUsageLimit() != null ? String.valueOf(sel.getUsageLimit()) : "");
        ComboBox<String> cbStatus   = new ComboBox<>(FXCollections.observableArrayList(
                "UPCOMING", "ACTIVE", "EXPIRED", "DISABLED"));

        cbType.setValue(sel.getDiscountType()); cbStatus.setValue(sel.getStatus());
        cbType.setMaxWidth(Double.MAX_VALUE);   cbStatus.setMaxWidth(Double.MAX_VALUE);
        dpStart.setMaxWidth(Double.MAX_VALUE);  dpEnd.setMaxWidth(Double.MAX_VALUE);
        tfMinOrder.setPromptText("0 = no minimum"); tfLimit.setPromptText("blank = ∞");

        Label lblInfo = new Label("Promo ID: " + sel.getPromoId()
                + "   |   Used: " + sel.getUsedCount()
                + (sel.getUsageLimit() != null ? " / " + sel.getUsageLimit() : " / ∞"));
        lblInfo.setStyle("-fx-text-fill:#555;-fx-font-size:12px;");

        Label lblErr = new Label();
        lblErr.setStyle("-fx-text-fill:#c62828;-fx-font-size:12px;"); lblErr.setWrapText(true);

        GridPane grid = buildGrid();
        int row = 0;
        grid.add(lblInfo,                       0, row, 2, 1); row++;
        grid.add(new Separator(),               0, row, 2, 1); row++;
        grid.add(sec("-- Basic Info"),          0, row, 2, 1); row++;
        grid.add(fl("Code *"),       0, row); grid.add(tfCode,     1, row++);
        grid.add(fl("Name *"),       0, row); grid.add(tfName,     1, row++);
        grid.add(fl("Status *"),     0, row); grid.add(cbStatus,   1, row++);
        grid.add(new Separator(),               0, row, 2, 1); row++;
        grid.add(sec("-- Discount"),            0, row, 2, 1); row++;
        grid.add(fl("Type *"),       0, row); grid.add(cbType,     1, row++);
        grid.add(fl("Value *"),      0, row); grid.add(tfValue,    1, row++);
        grid.add(fl("Min Order (₫)"),0, row); grid.add(tfMinOrder, 1, row++);
        grid.add(fl("Usage Limit"),  0, row); grid.add(tfLimit,    1, row++);
        grid.add(new Separator(),               0, row, 2, 1); row++;
        grid.add(sec("-- Period"),              0, row, 2, 1); row++;
        grid.add(fl("Start Date *"), 0, row); grid.add(dpStart,    1, row++);
        grid.add(fl("End Date *"),   0, row); grid.add(dpEnd,      1, row++);
        grid.add(new Separator(),               0, row, 2, 1); row++;
        grid.add(lblErr,                        0, row, 2, 1); row++;

        Button btnSave   = primaryBtn("Save Changes");
        Button btnCancel = new Button("Cancel");
        btnCancel.setStyle("-fx-background-radius:6;-fx-padding:8 18;");
        HBox btns = new HBox(10, btnCancel, btnSave); btns.setAlignment(Pos.CENTER_RIGHT);
        grid.add(btns, 0, row, 2, 1);

        btnSave.setOnAction(ev -> {
            lblErr.setText("");
            Promotion p = buildPromotion(tfCode, tfName, cbType, tfValue,
                    dpStart, dpEnd, tfMinOrder, tfLimit, cbStatus, lblErr);
            if (p == null) return;
            p.setPromoId(sel.getPromoId());
            p.setUsedCount(sel.getUsedCount());
            try {
                svc.updatePromotion(p);
                setStatus("Promotion [" + p.getCode() + "] updated.");
                loadData(); dlg.close();
            } catch (Exception ex) { lblErr.setText(ex.getMessage()); }
        });
        btnCancel.setOnAction(ev -> dlg.close());

        ScrollPane scroll = new ScrollPane(grid); scroll.setFitToWidth(true);
        dlg.setScene(new Scene(scroll, 500, 600)); dlg.showAndWait();
    }

    // ==================== DISABLE ====================
    @FXML public void handleDisable() {
        Promotion sel = promoTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Disable promotion \"" + sel.getCode() + "\"?\n\n"
                + "It will no longer be applicable to new orders.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Disable"); confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.YES) return;
            try {
                svc.disablePromotion(sel.getPromoId());
                setStatus("Promotion [" + sel.getCode() + "] disabled.");
                loadData();
            } catch (Exception ex) { alert(Alert.AlertType.ERROR, "Error", ex.getMessage()); }
        });
    }

    // ==================== HARD DELETE ====================
    @FXML public void handleDelete() {
        Promotion sel = promoTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Permanently delete promotion code \"" + sel.getCode() + "\"?\n\n"
                + "⚠ This action cannot be undone.\n"
                + "Requirement: this code must not have been used in any order.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm promotion deletion"); confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.YES) return;
            try {
                svc.deletePromotion(sel.getPromoId());
                setStatus("Permanently deleted promotion [" + sel.getCode() + "].");
                loadData();
            } catch (IllegalStateException ex) {
                alert(Alert.AlertType.ERROR, "Cannot delete", ex.getMessage());
            } catch (Exception ex) {
                alert(Alert.AlertType.ERROR, "Delete error", ex.getMessage());
            }
        });
    }

    // ── Shared form builder ────────────────────────────────────────────────
    private Promotion buildPromotion(TextField tfCode, TextField tfName,
                                      ComboBox<String> cbType, TextField tfValue,
                                      DatePicker dpStart, DatePicker dpEnd,
                                      TextField tfMinOrder, TextField tfLimit,
                                      ComboBox<String> cbStatus, Label lblErr) {
        String code = tfCode.getText().trim();
        String name = tfName.getText().trim();
        if (code.isEmpty()) { lblErr.setText("Code is required."); return null; }
        if (name.isEmpty()) { lblErr.setText("Name is required."); return null; }
        if (dpStart.getValue() == null) { lblErr.setText("Start date is required."); return null; }
        if (dpEnd.getValue()   == null) { lblErr.setText("End date is required.");   return null; }

        BigDecimal value;
        try { value = new BigDecimal(tfValue.getText().trim().replace(",", "")); }
        catch (NumberFormatException ex) { lblErr.setText("Discount value must be a valid number."); return null; }

        BigDecimal minOrder;
        try {
            String raw = tfMinOrder.getText().trim().replace(",", "");
            minOrder = raw.isBlank() ? BigDecimal.ZERO : new BigDecimal(raw);
        } catch (NumberFormatException ex) { lblErr.setText("Min order value must be a valid number."); return null; }

        Integer limit = null;
        String limitRaw = tfLimit.getText().trim();
        if (!limitRaw.isBlank()) {
            try { limit = Integer.parseInt(limitRaw); }
            catch (NumberFormatException ex) { lblErr.setText("Usage limit must be a whole number."); return null; }
        }

        Promotion p = new Promotion();
        p.setCode(code); p.setName(name);
        p.setDiscountType(cbType.getValue());
        p.setDiscountValue(value);
        p.setStartDate(dpStart.getValue().atStartOfDay());
        p.setEndDate(dpEnd.getValue().atTime(23, 59, 59));
        p.setMinOrderValue(minOrder);
        p.setUsageLimit(limit);
        p.setUsedCount(0);
        p.setStatus(cbStatus.getValue());
        return p;
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
    private Button primaryBtn(String t) {
        Button b = new Button(t);
        b.setStyle("-fx-background-color:#3949ab;-fx-text-fill:white;-fx-background-radius:6;-fx-padding:8 18;-fx-font-weight:bold;");
        return b;
    }
    private void setStatus(String msg) { if (statusBarLabel != null) statusBarLabel.setText(msg); }
    private void alert(Alert.AlertType t, String title, String msg) {
        Alert a = new Alert(t); a.setTitle(title); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }
}