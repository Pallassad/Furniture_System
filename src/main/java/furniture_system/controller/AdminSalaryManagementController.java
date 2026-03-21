package furniture_system.controller;

import furniture_system.model.Salary;
import furniture_system.service.SalaryService;
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
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Admin – Salary Management
 * Fullwidth table + Modal Dialog pattern (same as AuthController).
 */
public class AdminSalaryManagementController {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final List<String>      STATUSES  = List.of("DRAFT", "CONFIRMED", "PAID");

    // ── Table ──────────────────────────────────────────────────────────────
    @FXML private TableView<Salary>           tableSalary;
    @FXML private TableColumn<Salary, String> colSalaryId;
    @FXML private TableColumn<Salary, String> colEmployee;
    @FXML private TableColumn<Salary, String> colMonth;
    @FXML private TableColumn<Salary, String> colBase;
    @FXML private TableColumn<Salary, String> colAllowance;
    @FXML private TableColumn<Salary, String> colBonus;
    @FXML private TableColumn<Salary, String> colDeduction;
    @FXML private TableColumn<Salary, String> colFinal;
    @FXML private TableColumn<Salary, String> colSalaryStatus;
    @FXML private TableColumn<Salary, String> colPaidDate;

    // ── Search & Toolbar ───────────────────────────────────────────────────
    @FXML private TextField txtSearch;
    @FXML private Button    btnAdd;
    @FXML private Button    btnEdit;
    @FXML private Button    btnDelete;
    @FXML private Label     lblSalaryStatus;

    private final SalaryService              service = new SalaryService();
    private final ObservableList<Salary>     data    = FXCollections.observableArrayList();

    // ══════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        setupTable();
        loadAll();

        tableSalary.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> {
                    boolean has = sel != null;
                    btnEdit.setDisable(!has);
                    boolean canDel = has && "DRAFT".equals(sel.getStatus());
                    btnDelete.setDisable(!canDel);
                });
        btnEdit.setDisable(true);
        btnDelete.setDisable(true);
    }

    // ── Table setup ────────────────────────────────────────────────────────
    private void setupTable() {
        colSalaryId.setCellValueFactory(c ->
                new SimpleStringProperty(String.valueOf(c.getValue().getSalaryId())));
        colEmployee.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getEmployeeName()));
        colMonth.setCellValueFactory(c -> {
            LocalDate d = c.getValue().getSalaryMonth();
            return new SimpleStringProperty(d != null ? d.format(MONTH_FMT) : "—");
        });
        colBase.setCellValueFactory(c ->
                new SimpleStringProperty(fmt(c.getValue().getBaseSalary())));
        colAllowance.setCellValueFactory(c ->
                new SimpleStringProperty(fmt(c.getValue().getAllowance())));
        colBonus.setCellValueFactory(c ->
                new SimpleStringProperty(fmtSigned(c.getValue().getBonus(), false)));
        colDeduction.setCellValueFactory(c ->
                new SimpleStringProperty(fmtSigned(c.getValue().getDeduction(), true)));
        colFinal.setCellValueFactory(c ->
                new SimpleStringProperty(fmt(c.getValue().getFinalSalary())));
        colSalaryStatus.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getStatus()));
        colPaidDate.setCellValueFactory(c -> {
            LocalDate pd = c.getValue().getPaidDate();
            return new SimpleStringProperty(pd != null ? pd.toString() : "—");
        });

        // Status color
        colSalaryStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); setStyle(""); return; }
                setText(s);
                setStyle(switch (s) {
                    case "PAID"      -> "-fx-text-fill:#27ae60;-fx-font-weight:bold;";
                    case "CONFIRMED" -> "-fx-text-fill:#1565c0;-fx-font-weight:bold;";
                    case "DRAFT"     -> "-fx-text-fill:#888;";
                    default          -> "";
                });
            }
        });

        // Final salary color
        colFinal.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); setStyle(""); return; }
                setText(s);
                setStyle("-fx-font-weight:bold;-fx-text-fill:#1a237e;");
            }
        });

        // Row highlight
        tableSalary.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Salary item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setStyle(""); return; }
                setStyle(switch (item.getStatus()) {
                    case "PAID"  -> "-fx-background-color:#e8f5e9;";
                    case "DRAFT" -> "-fx-background-color:#f5f5f5;";
                    default      -> "";
                });
            }
        });

        tableSalary.setItems(data);
        tableSalary.setPlaceholder(new Label("No salary records found."));
    }

    // ── Load & Search ──────────────────────────────────────────────────────
    private void loadAll() {
        try {
            data.setAll(service.getAll());
            setStatus("Loaded " + data.size() + " record(s).", false);
        } catch (SQLException e) { alert(Alert.AlertType.ERROR, "Error", e.getMessage()); }
    }

    @FXML public void handleRefresh() { txtSearch.clear(); loadAll(); }

    @FXML public void handleSearch() {
        String kw = txtSearch.getText().trim();
        try {
            List<Salary> result = kw.isBlank() ? service.getAll() : service.search(kw);
            data.setAll(result);
            setStatus(result.size() + " result(s).", false);
        } catch (SQLException e) { alert(Alert.AlertType.ERROR, "Search Error", e.getMessage()); }
    }

    // ==================== ADD DIALOG ====================
    @FXML public void handleAdd() {
        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Add New Salary Record");

        TextField        tfEmpId    = new TextField();
        DatePicker       dpMonth    = new DatePicker();
        TextField        tfBase     = new TextField();
        TextField        tfAllow    = new TextField("0");
        TextField        tfBonus    = new TextField("0");
        ComboBox<String> cbStatus   = new ComboBox<>(FXCollections.observableArrayList(STATUSES));
        DatePicker       dpPaidDate = new DatePicker();
        TextArea         taNote     = new TextArea();

        cbStatus.setValue("DRAFT"); cbStatus.setMaxWidth(Double.MAX_VALUE);
        dpMonth.setMaxWidth(Double.MAX_VALUE); dpPaidDate.setMaxWidth(Double.MAX_VALUE);
        taNote.setPrefRowCount(3); taNote.setWrapText(true);
        tfEmpId.setPromptText("e.g. 1");
        tfBase.setPromptText("e.g. 8000000");
        tfAllow.setPromptText("0  or  5%");
        tfBonus.setPromptText("+500000  /  -200000  /  +10%  /  -5%");

        // Live preview
        Label lblPreview = new Label("Net Salary: —");
        lblPreview.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:#9e9e9e;");
        Label lblBonusHint = new Label();
        lblBonusHint.setStyle("-fx-font-size:10px;-fx-font-style:italic;");

        Runnable updatePreview = () -> updatePreviewLabels(tfBase, tfAllow, tfBonus, lblPreview, lblBonusHint);
        tfBase.textProperty().addListener((o, a, b)  -> updatePreview.run());
        tfAllow.textProperty().addListener((o, a, b) -> updatePreview.run());
        tfBonus.textProperty().addListener((o, a, b) -> updatePreview.run());

        Label lblErr = new Label();
        lblErr.setStyle("-fx-text-fill:#c62828;-fx-font-size:12px;"); lblErr.setWrapText(true);

        GridPane grid = buildGrid();
        int row = 0;
        grid.add(sec("-- Employee & Month"), 0, row, 2, 1); row++;
        grid.add(fl("Employee ID *"),   0, row); grid.add(tfEmpId,    1, row++);
        grid.add(fl("Salary Month *"),   0, row); grid.add(dpMonth,    1, row++);
        grid.add(new Separator(),                0, row, 2, 1); row++;
        grid.add(sec("-- Salary & Allowance"),      0, row, 2, 1); row++;
        grid.add(fl("Base Salary *"),  0, row); grid.add(tfBase,     1, row++);
        grid.add(fl("Allowance"),         0, row); grid.add(tfAllow,    1, row++);
        grid.add(new Label(""),          0, row); grid.add(hint("Amount (e.g. 500000) or % (e.g. 5%)"), 1, row++);
        grid.add(fl("Bonus / Penalty"),   0, row); grid.add(tfBonus,    1, row++);
        grid.add(new Label(""),          0, row); grid.add(lblBonusHint, 1, row++);
        grid.add(new Label(""),          0, row); grid.add(hint("+500000 bonus  |  -200000 penalty  |  +10%  |  -5%"), 1, row++);
        grid.add(new Label(""),          0, row); grid.add(lblPreview, 1, row++);
        grid.add(new Separator(),                0, row, 2, 1); row++;
        grid.add(sec("-- Status & Payment"), 0, row, 2, 1); row++;
        grid.add(fl("Status *"),    0, row); grid.add(cbStatus,   1, row++);
        grid.add(fl("Payment Date"), 0, row); grid.add(dpPaidDate, 1, row++);
        grid.add(new Label(""),          0, row); grid.add(hint("Required when status is PAID."), 1, row++);
        grid.add(fl("Note"),         0, row); grid.add(taNote,     1, row++);
        grid.add(new Separator(),                0, row, 2, 1); row++;
        grid.add(lblErr,                         0, row, 2, 1); row++;

        Button btnSave   = primaryBtn("➕  Add Record");
        Button btnCancel = new Button("Cancel");
        btnCancel.setStyle("-fx-background-radius:6;-fx-padding:8 18;");
        HBox btns = new HBox(10, btnCancel, btnSave); btns.setAlignment(Pos.CENTER_RIGHT);
        grid.add(btns, 0, row, 2, 1);

        btnSave.setOnAction(ev -> {
            lblErr.setText("");
            try {
                int       empId = parseEmpId(tfEmpId, lblErr); if (empId < 0) return;
                LocalDate month = parseMonth(dpMonth, lblErr);  if (month == null) return;
                BigDecimal base = parseBase(tfBase, lblErr);    if (base == null) return;
                int id = service.addSalary(empId, month, base,
                        tfAllow.getText(), tfBonus.getText(),
                        cbStatus.getValue(), dpPaidDate.getValue(),
                        taNote.getText());
                setStatus("Salary record #" + id + ".", false);
                loadAll(); dlg.close();
            } catch (IllegalArgumentException ex) { lblErr.setText(ex.getMessage()); }
              catch (SQLException ex) { lblErr.setText("Error DB: " + ex.getMessage()); }
        });
        btnCancel.setOnAction(ev -> dlg.close());

        ScrollPane scroll = new ScrollPane(grid); scroll.setFitToWidth(true);
        dlg.setScene(new Scene(scroll, 520, 680)); dlg.showAndWait();
    }

    // ==================== EDIT DIALOG ====================
    @FXML public void handleEdit() {
        Salary sel = tableSalary.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Edit Salary - " + sel.getEmployeeName()
                + " [" + (sel.getSalaryMonth() != null ? sel.getSalaryMonth().format(MONTH_FMT) : "") + "]");

        boolean isPaid = "PAID".equals(sel.getStatus());

        TextField        tfEmpId    = new TextField(String.valueOf(sel.getEmployeeId()));
        DatePicker       dpMonth    = new DatePicker(sel.getSalaryMonth());
        TextField        tfBase     = new TextField(sel.getBaseSalary() != null ? sel.getBaseSalary().toPlainString() : "");
        TextField        tfAllow    = new TextField(sel.getAllowance()  != null ? sel.getAllowance().toPlainString()  : "0");
        ComboBox<String> cbStatus   = new ComboBox<>(FXCollections.observableArrayList(STATUSES));
        DatePicker       dpPaidDate = new DatePicker(sel.getPaidDate());
        TextArea         taNote     = new TextArea(sel.getNote() != null ? sel.getNote() : "");

        // Merge bonus/deduction into signed value
        BigDecimal bonusVal = sel.getBonus()     != null ? sel.getBonus()     : BigDecimal.ZERO;
        BigDecimal dedVal   = sel.getDeduction() != null ? sel.getDeduction() : BigDecimal.ZERO;
        TextField tfBonus = new TextField(bonusVal.subtract(dedVal).toPlainString());

        cbStatus.setValue(sel.getStatus()); cbStatus.setMaxWidth(Double.MAX_VALUE);
        dpMonth.setMaxWidth(Double.MAX_VALUE); dpPaidDate.setMaxWidth(Double.MAX_VALUE);
        taNote.setPrefRowCount(3); taNote.setWrapText(true);

        // Disable fields if PAID
        tfEmpId.setDisable(isPaid); dpMonth.setDisable(isPaid);
        tfBase.setDisable(isPaid); tfAllow.setDisable(isPaid);
        tfBonus.setDisable(isPaid); taNote.setDisable(isPaid);

        Label lblInfo = new Label("ID: " + sel.getSalaryId()
                + "   |   Employee: " + sel.getEmployeeName()
                + (isPaid ? "   |   🔒 PAID — read only" : ""));
        lblInfo.setStyle("-fx-text-fill:#555;-fx-font-size:12px;");

        Label lblPreview   = new Label("Net Salary: —");
        lblPreview.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:#1a237e;");
        Label lblBonusHint = new Label();
        lblBonusHint.setStyle("-fx-font-size:10px;-fx-font-style:italic;");

        Runnable updatePreview = () -> updatePreviewLabels(tfBase, tfAllow, tfBonus, lblPreview, lblBonusHint);
        tfBase.textProperty().addListener((o, a, b)  -> updatePreview.run());
        tfAllow.textProperty().addListener((o, a, b) -> updatePreview.run());
        tfBonus.textProperty().addListener((o, a, b) -> updatePreview.run());
        updatePreview.run();

        Label lblErr = new Label();
        lblErr.setStyle("-fx-text-fill:#c62828;-fx-font-size:12px;"); lblErr.setWrapText(true);

        GridPane grid = buildGrid();
        int row = 0;
        grid.add(lblInfo,                        0, row, 2, 1); row++;
        grid.add(new Separator(),                0, row, 2, 1); row++;
        grid.add(sec("-- Employee & Month"), 0, row, 2, 1); row++;
        grid.add(fl("Employee ID *"),   0, row); grid.add(tfEmpId,    1, row++);
        grid.add(fl("Salary Month *"),   0, row); grid.add(dpMonth,    1, row++);
        grid.add(new Separator(),                0, row, 2, 1); row++;
        grid.add(sec("-- Salary & Allowance"),      0, row, 2, 1); row++;
        grid.add(fl("Base Salary *"),  0, row); grid.add(tfBase,     1, row++);
        grid.add(fl("Allowance"),         0, row); grid.add(tfAllow,    1, row++);
        grid.add(fl("Bonus / Penalty"),   0, row); grid.add(tfBonus,    1, row++);
        grid.add(new Label(""),          0, row); grid.add(lblBonusHint, 1, row++);
        grid.add(new Label(""),          0, row); grid.add(lblPreview, 1, row++);
        grid.add(new Separator(),                0, row, 2, 1); row++;
        grid.add(sec("-- Status & Payment"), 0, row, 2, 1); row++;
        grid.add(fl("Status *"),    0, row); grid.add(cbStatus,   1, row++);
        grid.add(fl("Payment Date"), 0, row); grid.add(dpPaidDate, 1, row++);
        grid.add(fl("Note"),         0, row); grid.add(taNote,     1, row++);
        grid.add(new Separator(),                0, row, 2, 1); row++;
        grid.add(lblErr,                         0, row, 2, 1); row++;

        Button btnSave   = primaryBtn(isPaid ? "Locked (PAID)" : "✏  Save Changes");
        btnSave.setDisable(isPaid);
        Button btnCancel = new Button("Close");
        btnCancel.setStyle("-fx-background-radius:6;-fx-padding:8 18;");
        HBox btns = new HBox(10, btnCancel, btnSave); btns.setAlignment(Pos.CENTER_RIGHT);
        grid.add(btns, 0, row, 2, 1);

        btnSave.setOnAction(ev -> {
            lblErr.setText("");
            try {
                int       empId = parseEmpId(tfEmpId, lblErr); if (empId < 0) return;
                LocalDate month = parseMonth(dpMonth, lblErr);  if (month == null) return;
                BigDecimal base = parseBase(tfBase, lblErr);    if (base == null) return;
                service.updateSalary(sel.getSalaryId(), empId, month, base,
                        tfAllow.getText(), tfBonus.getText(),
                        cbStatus.getValue(), dpPaidDate.getValue(),
                        taNote.getText());
                setStatus("Updated record #" + sel.getSalaryId() + ".", false);
                loadAll(); dlg.close();
            } catch (IllegalArgumentException ex) { lblErr.setText(ex.getMessage()); }
              catch (SQLException ex) { lblErr.setText("Error DB: " + ex.getMessage()); }
        });
        btnCancel.setOnAction(ev -> dlg.close());

        ScrollPane scroll = new ScrollPane(grid); scroll.setFitToWidth(true);
        dlg.setScene(new Scene(scroll, 520, 660)); dlg.showAndWait();
    }

    // ==================== DELETE ====================
    @FXML public void handleDelete() {
        Salary sel = tableSalary.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        if (!"DRAFT".equals(sel.getStatus())) {
            alert(Alert.AlertType.WARNING, "Not Allowed",
                    "Only DRAFT records can be deleted.\nCurrent status: " + sel.getStatus());
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete salary record #" + sel.getSalaryId() + "?\n"
                + "Employee: " + sel.getEmployeeName() + "\n"
                + "Month: " + (sel.getSalaryMonth() != null ? sel.getSalaryMonth().format(MONTH_FMT) : "—") + "\n\n"
                + "This action cannot be undone.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Delete"); confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.YES) return;
            try {
                service.deleteSalary(sel.getSalaryId());
                setStatus("Deleted record #" + sel.getSalaryId() + ".", false);
                loadAll();
            } catch (Exception ex) { alert(Alert.AlertType.ERROR, "Delete Error", ex.getMessage()); }
        });
    }

    // ── Live preview helper ────────────────────────────────────────────────
    private void updatePreviewLabels(TextField tfBase, TextField tfAllow, TextField tfBonus,
                                      Label lblPreview, Label lblBonusHint) {
        try {
            BigDecimal base = parseBDSafe(tfBase.getText(), BigDecimal.ZERO);
            BigDecimal allow = service.parseAmount(tfAllow.getText(), base, false);
            BigDecimal bonusSigned = service.parseAmount(tfBonus.getText(), base, true);

            BigDecimal bonus, deduction;
            if (bonusSigned.compareTo(BigDecimal.ZERO) >= 0) {
                bonus = bonusSigned; deduction = BigDecimal.ZERO;
            } else {
                bonus = BigDecimal.ZERO; deduction = bonusSigned.negate();
            }
            BigDecimal fin = base.add(allow).add(bonus).subtract(deduction);
            lblPreview.setText("Net Salary: " + String.format("%,.0f ₫", fin));
            lblPreview.setStyle("-fx-font-size:13px;-fx-font-weight:bold;"
                    + (fin.compareTo(BigDecimal.ZERO) < 0
                    ? "-fx-text-fill:#c62828;" : "-fx-text-fill:#27ae60;"));

            // Bonus hint
            String desc = service.describeBonusAmount(tfBonus.getText(), base);
            lblBonusHint.setText(desc);
            lblBonusHint.setStyle(desc.contains("Penalty")
                    ? "-fx-text-fill:#c62828;-fx-font-size:10px;-fx-font-style:italic;"
                    : "-fx-text-fill:#27ae60;-fx-font-size:10px;-fx-font-style:italic;");
        } catch (Exception e) {
            lblPreview.setText("Net Salary: —");
            lblPreview.setStyle("-fx-font-size:13px;-fx-font-weight:bold;-fx-text-fill:#9e9e9e;");
            lblBonusHint.setText("");
        }
    }

    // ── Input parsers ──────────────────────────────────────────────────────
    private int parseEmpId(TextField tf, Label lblErr) {
        try {
            int id = Integer.parseInt(tf.getText().trim());
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException e) {
            lblErr.setText("Employee ID must be a positive integer.");
            return -1;
        }
    }

    private LocalDate parseMonth(DatePicker dp, Label lblErr) {
        if (dp.getValue() == null) { lblErr.setText("Please select a salary month."); return null; }
        return dp.getValue();
    }

    private BigDecimal parseBase(TextField tf, Label lblErr) {
        try {
            BigDecimal b = new BigDecimal(tf.getText().trim().replace(".", "").replace(",", ""));
            if (b.compareTo(BigDecimal.ZERO) <= 0) throw new NumberFormatException();
            return b;
        } catch (Exception e) { lblErr.setText("Base salary must be a positive number."); return null; }
    }

    private BigDecimal parseBDSafe(String text, BigDecimal def) {
        try { return new BigDecimal(text.trim().replace(".", "").replace(",", "")); }
        catch (Exception e) { return def; }
    }

    // ── UI helpers ─────────────────────────────────────────────────────────
    private String fmt(BigDecimal val) {
        return val != null ? String.format("%,.0f ₫", val) : "—";
    }

    private String fmtSigned(BigDecimal val, boolean isDeduction) {
        if (val == null || val.compareTo(BigDecimal.ZERO) == 0) return "—";
        return isDeduction ? "-" + String.format("%,.0f ₫", val)
                           : "+" + String.format("%,.0f ₫", val);
    }

    private GridPane buildGrid() {
        GridPane g = new GridPane(); g.setHgap(12); g.setVgap(10); g.setPadding(new Insets(24));
        g.getColumnConstraints().addAll(new ColumnConstraints(145), new ColumnConstraints(310));
        return g;
    }

    private Label fl(String t)   { Label l = new Label(t); l.setStyle("-fx-font-weight:bold;-fx-font-size:12px;"); return l; }
    private Label sec(String t)  { Label l = new Label(t); l.setStyle("-fx-font-weight:bold;-fx-font-size:11px;-fx-text-fill:#3949ab;"); return l; }
    private Label hint(String t) { Label l = new Label(t); l.setStyle("-fx-font-size:10px;-fx-text-fill:#888;"); l.setWrapText(true); return l; }
    private Button primaryBtn(String t) {
        Button b = new Button(t);
        b.setStyle("-fx-background-color:#3949ab;-fx-text-fill:white;-fx-background-radius:6;-fx-padding:8 18;-fx-font-weight:bold;");
        return b;
    }
    private void setStatus(String msg, boolean isError) {
        if (lblSalaryStatus == null) return;
        lblSalaryStatus.setText(msg);
        lblSalaryStatus.setStyle(isError ? "-fx-text-fill:#c62828;-fx-font-size:12px;"
                                         : "-fx-text-fill:#37474f;-fx-font-size:12px;");
    }
    private void alert(Alert.AlertType t, String title, String msg) {
        Alert a = new Alert(t); a.setTitle(title); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }
}