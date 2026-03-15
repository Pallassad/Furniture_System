package furniture_system.controller;

import furniture_system.model.Salary;
import furniture_system.service.SalaryService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;

import java.math.BigDecimal;
import java.net.URL;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * AdminSalaryManagementController
 *
 * Bonus/Phạt gộp vào một field duy nhất:
 *   +500000  hoặc  500000  → Thưởng
 *   -200000                → Phạt
 *   +10%  hoặc  10%        → Thưởng % lương
 *   -5%                    → Phạt % lương
 *
 * Service tự tách vào cột Bonus / Deduction khi lưu DB.
 */
public class AdminSalaryManagementController implements Initializable {

    // ── Table ────────────────────────────────────────────────
    @FXML private TableView<Salary>            tableSalary;
    @FXML private TableColumn<Salary, String>  colSalaryId;
    @FXML private TableColumn<Salary, String>  colEmployee;
    @FXML private TableColumn<Salary, String>  colMonth;
    @FXML private TableColumn<Salary, String>  colBase;
    @FXML private TableColumn<Salary, String>  colAllowance;
    @FXML private TableColumn<Salary, String>  colBonus;
    @FXML private TableColumn<Salary, String>  colDeduction;
    @FXML private TableColumn<Salary, String>  colFinal;
    @FXML private TableColumn<Salary, String>  colSalaryStatus;
    @FXML private TableColumn<Salary, String>  colPaidDate;

    // ── Search ───────────────────────────────────────────────
    @FXML private TextField txtSearch;

    // ── Form fields ──────────────────────────────────────────
    @FXML private TextField        txtEmployeeId;
    @FXML private DatePicker       dpSalaryMonth;
    @FXML private TextField        txtBaseSalary;
    @FXML private TextField        txtAllowance;
    @FXML private TextField        txtBonus;       // signed: +500000 / -200000 / +10% / -5%

    @FXML private Label            lblFinalPreview;

    // ── Hint labels ──────────────────────────────────────────
    @FXML private Label            lblAllowanceHint;
    @FXML private Label            lblBonusHint;   // "= +800.000 ₫  🎉 Thưởng"  /  "= -200.000 ₫  ⚠ Phạt"

    @FXML private ComboBox<String> cmbSalaryStatus;
    @FXML private DatePicker       dpPaidDate;
    @FXML private TextArea         txtSalaryNote;

    // ── Buttons ──────────────────────────────────────────────
    @FXML private Button btnSalaryAdd;
    @FXML private Button btnSalaryUpdate;
    @FXML private Button btnSalaryDelete;
    @FXML private Button btnSalaryClear;

    // ── Status bar ───────────────────────────────────────────
    @FXML private Label lblSalaryStatus;

    // ── State ────────────────────────────────────────────────
    private final SalaryService service = new SalaryService();
    private final ObservableList<Salary> data = FXCollections.observableArrayList();

    private static final List<String> STATUSES = List.of("DRAFT", "CONFIRMED", "PAID");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    // ─────────────────────────────────────────────────────────
    // INIT
    // ─────────────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupTable();
        cmbSalaryStatus.setItems(FXCollections.observableArrayList(STATUSES));
        cmbSalaryStatus.setValue("DRAFT");

        tableSalary.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> populateForm(sel));

        attachLivePreview();
        loadAll();
    }

    // ─────────────────────────────────────────────────────────
    // LIVE PREVIEW
    // ─────────────────────────────────────────────────────────

    private void attachLivePreview() {
        Runnable update = () -> {
            BigDecimal base = parseBDSafe(txtBaseSalary.getText(), BigDecimal.ZERO);

            // Hint allowance (chỉ khi nhập %)
            updateAllowanceHint(base);

            // Hint bonus (hiện khi nhập %, +, hoặc -)
            updateBonusHint(base);

            // Preview lương cuối
            try {
                BigDecimal allow       = service.parseAmount(txtAllowance.getText(), base, false);
                BigDecimal bonusSigned = service.parseAmount(txtBonus.getText(),     base, true);

                // Tính FinalSalary như DB: base + allowance + bonus - deduction
                BigDecimal bonus, deduction;
                if (bonusSigned.compareTo(BigDecimal.ZERO) >= 0) {
                    bonus     = bonusSigned;
                    deduction = BigDecimal.ZERO;
                } else {
                    bonus     = BigDecimal.ZERO;
                    deduction = bonusSigned.negate();
                }
                BigDecimal fin = base.add(allow).add(bonus).subtract(deduction);

                lblFinalPreview.setText("Lương thực nhận: " + String.format("%,.0f ₫", fin));
                lblFinalPreview.setStyle(fin.compareTo(BigDecimal.ZERO) < 0
                        ? "-fx-font-size:14px; -fx-font-weight:bold; -fx-text-fill:#c62828;"
                        : "-fx-font-size:14px; -fx-font-weight:bold; -fx-text-fill:#2e7d32;");
            } catch (Exception e) {
                lblFinalPreview.setText("Lương thực nhận: —");
                lblFinalPreview.setStyle("-fx-font-size:14px; -fx-font-weight:bold; -fx-text-fill:#9e9e9e;");
            }
        };

        txtBaseSalary.textProperty().addListener((o, a, b) -> update.run());
        txtAllowance .textProperty().addListener((o, a, b) -> update.run());
        txtBonus     .textProperty().addListener((o, a, b) -> update.run());
    }

    private void updateAllowanceHint(BigDecimal base) {
        if (lblAllowanceHint == null) return;
        String input = txtAllowance.getText();
        if (input == null || !input.strip().endsWith("%")) {
            lblAllowanceHint.setText(""); lblAllowanceHint.setVisible(false); lblAllowanceHint.setManaged(false);
            return;
        }
        try {
            BigDecimal amt = service.parseAmount(input, base, false);
            String text = "= +" + String.format("%,.0f ₫", amt);
            lblAllowanceHint.setText(text);
            lblAllowanceHint.setVisible(true);
            lblAllowanceHint.setManaged(true);
        } catch (Exception e) {
            lblAllowanceHint.setText("⛔ " + e.getMessage());
            lblAllowanceHint.setVisible(true);
            lblAllowanceHint.setManaged(true);
        }
    }

    private void updateBonusHint(BigDecimal base) {
        if (lblBonusHint == null) return;
        String desc = service.describeBonusAmount(txtBonus.getText(), base);
        if (desc.isEmpty()) {
            lblBonusHint.setText(""); lblBonusHint.setVisible(false); lblBonusHint.setManaged(false);
            return;
        }
        lblBonusHint.setText(desc);
        lblBonusHint.setVisible(true);
        lblBonusHint.setManaged(true);

        // Màu hint: đỏ = phạt, xanh = thưởng
        boolean isPenalty = desc.contains("Phạt");
        lblBonusHint.setStyle(isPenalty
                ? "-fx-text-fill:#c62828; -fx-font-size:11px; -fx-font-style:italic;"
                : "-fx-text-fill:#2e7d32; -fx-font-size:11px; -fx-font-style:italic;");
    }

    // ─────────────────────────────────────────────────────────
    // TABLE SETUP
    // ─────────────────────────────────────────────────────────

    private void setupTable() {
        colSalaryId    .setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getSalaryId())));
        colEmployee    .setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEmployeeName()));
        colMonth       .setCellValueFactory(c -> {
            LocalDate d = c.getValue().getSalaryMonth();
            return new SimpleStringProperty(d != null ? d.format(MONTH_FMT) : "");
        });
        colBase        .setCellValueFactory(c -> new SimpleStringProperty(fmt(c.getValue().getBaseSalary())));
        colAllowance   .setCellValueFactory(c -> new SimpleStringProperty(fmt(c.getValue().getAllowance())));
        colBonus       .setCellValueFactory(c -> new SimpleStringProperty(fmtSigned(c.getValue().getBonus(), false)));
        colDeduction   .setCellValueFactory(c -> new SimpleStringProperty(fmtSigned(c.getValue().getDeduction(), true)));
        colFinal       .setCellValueFactory(c -> new SimpleStringProperty(fmt(c.getValue().getFinalSalary())));
        colSalaryStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus()));
        colPaidDate    .setCellValueFactory(c -> {
            LocalDate pd = c.getValue().getPaidDate();
            return new SimpleStringProperty(pd != null ? pd.toString() : "—");
        });

        tableSalary.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Salary item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("row-paid", "row-inactive");
                if (item == null || empty) return;
                if ("PAID".equals(item.getStatus()))       getStyleClass().add("row-paid");
                else if ("DRAFT".equals(item.getStatus())) getStyleClass().add("row-inactive");
            }
        });

        tableSalary.setItems(data);
        tableSalary.setPlaceholder(new Label("Không có bản ghi lương."));
    }

    // ─────────────────────────────────────────────────────────
    // DATA LOADING
    // ─────────────────────────────────────────────────────────

    private void loadAll() {
        try {
            data.setAll(service.getAll());
            setStatus("Đã tải " + data.size() + " bản ghi.", false);
        } catch (SQLException e) {
            showError("Lỗi tải dữ liệu", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────
    // SEARCH
    // ─────────────────────────────────────────────────────────

    @FXML
    private void handleSearch() {
        String kw = txtSearch.getText().trim();
        try {
            List<Salary> result = service.search(kw);
            data.setAll(result);
            setStatus(result.size() + " kết quả.", false);
        } catch (SQLException e) {
            showError("Lỗi tìm kiếm", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────
    // ADD
    // ─────────────────────────────────────────────────────────

    @FXML
    private void handleSalaryAdd() {
        try {
            int        empId = parseEmployeeId();
            LocalDate  month = parseMonth();
            BigDecimal base  = parseBaseSalary();
            if (empId < 0 || month == null || base == null) return;

            int id = service.addSalary(
                    empId, month, base,
                    txtAllowance.getText(),
                    txtBonus.getText(),
                    cmbSalaryStatus.getValue(),
                    dpPaidDate.getValue(),
                    txtSalaryNote.getText());

            setStatus("Đã tạo bản ghi lương #" + id + ".", false);
            loadAll();
            clearForm();

        } catch (IllegalArgumentException e) {
            showError("Lỗi nhập liệu", e.getMessage());
        } catch (SQLException e) {
            showError("Lỗi lưu DB", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────
    // UPDATE
    // ─────────────────────────────────────────────────────────

    @FXML
    private void handleSalaryUpdate() {
        Salary selected = tableSalary.getSelectionModel().getSelectedItem();
        if (selected == null) { showError("Chưa chọn", "Vui lòng chọn bản ghi cần cập nhật."); return; }

        try {
            int        empId = parseEmployeeId();
            LocalDate  month = parseMonth();
            BigDecimal base  = parseBaseSalary();
            if (empId < 0 || month == null || base == null) return;

            service.updateSalary(
                    selected.getSalaryId(),
                    empId, month, base,
                    txtAllowance.getText(),
                    txtBonus.getText(),
                    cmbSalaryStatus.getValue(),
                    dpPaidDate.getValue(),
                    txtSalaryNote.getText());

            setStatus("Đã cập nhật bản ghi #" + selected.getSalaryId() + ".", false);
            loadAll();
            clearForm();

        } catch (IllegalArgumentException e) {
            showError("Lỗi nhập liệu", e.getMessage());
        } catch (SQLException e) {
            showError("Lỗi cập nhật", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────
    // DELETE
    // ─────────────────────────────────────────────────────────

    @FXML
    private void handleSalaryDelete() {
        Salary selected = tableSalary.getSelectionModel().getSelectedItem();
        if (selected == null) { showError("Chưa chọn", "Vui lòng chọn bản ghi cần xoá."); return; }

        if (!"DRAFT".equals(selected.getStatus())) {
            showError("Không được phép", "Chỉ xoá được bản ghi DRAFT. Trạng thái hiện tại: " + selected.getStatus());
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận xoá");
        confirm.setHeaderText("Xoá bản ghi lương #" + selected.getSalaryId() + "?");
        confirm.setContentText(
                "Nhân viên: " + selected.getEmployeeName()
                + "\nTháng: " + selected.getSalaryMonth()
                + "\nHành động này không thể hoàn tác.");
        Optional<ButtonType> res = confirm.showAndWait();
        if (res.isEmpty() || res.get() != ButtonType.OK) return;

        try {
            service.deleteSalary(selected.getSalaryId());
            setStatus("Đã xoá bản ghi #" + selected.getSalaryId() + ".", false);
            loadAll();
            clearForm();
        } catch (SQLException | IllegalArgumentException e) {
            showError("Lỗi xoá", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────
    // CLEAR
    // ─────────────────────────────────────────────────────────

    @FXML
    private void handleSalaryClear() { clearForm(); }

    // ─────────────────────────────────────────────────────────
    // FORM HELPERS
    // ─────────────────────────────────────────────────────────

    /**
     * Điền form từ hàng được chọn.
     * Khi load lại: bonus > 0 hiện dương, deduction > 0 hiện âm trong field Bonus.
     */
    private void populateForm(Salary s) {
        if (s == null) return;
        txtEmployeeId  .setText(String.valueOf(s.getEmployeeId()));
        dpSalaryMonth  .setValue(s.getSalaryMonth());
        txtBaseSalary  .setText(s.getBaseSalary() != null ? s.getBaseSalary().toPlainString() : "");
        txtAllowance   .setText(s.getAllowance()  != null ? s.getAllowance().toPlainString()   : "0");

        // Gộp bonus/deduction ngược lại thành signed value cho field Bonus
        BigDecimal bonusVal = s.getBonus()     != null ? s.getBonus()     : BigDecimal.ZERO;
        BigDecimal dedVal   = s.getDeduction() != null ? s.getDeduction() : BigDecimal.ZERO;
        BigDecimal signed   = bonusVal.subtract(dedVal);  // nếu deduction > 0 thì signed âm
        txtBonus.setText(signed.toPlainString());

        cmbSalaryStatus.setValue(s.getStatus());
        dpPaidDate     .setValue(s.getPaidDate());
        txtSalaryNote  .setText(s.getNote() != null ? s.getNote() : "");

        setFormDisabled("PAID".equals(s.getStatus()));
    }

    private void clearForm() {
        txtEmployeeId.clear();
        dpSalaryMonth.setValue(null);
        txtBaseSalary.clear();
        txtAllowance .setText("0");
        txtBonus     .setText("0");
        cmbSalaryStatus.setValue("DRAFT");
        dpPaidDate.setValue(null);
        txtSalaryNote.clear();
        lblFinalPreview.setText("Lương thực nhận: —");
        lblFinalPreview.setStyle("-fx-font-size:14px; -fx-font-weight:bold; -fx-text-fill:#9e9e9e;");
        hideHints();
        tableSalary.getSelectionModel().clearSelection();
        setFormDisabled(false);
        setStatus("Sẵn sàng.", false);
    }

    private void setFormDisabled(boolean disabled) {
        txtEmployeeId  .setDisable(disabled);
        dpSalaryMonth  .setDisable(disabled);
        txtBaseSalary  .setDisable(disabled);
        txtAllowance   .setDisable(disabled);
        txtBonus       .setDisable(disabled);
        cmbSalaryStatus.setDisable(disabled);
        dpPaidDate     .setDisable(disabled);
        txtSalaryNote  .setDisable(disabled);
        btnSalaryUpdate.setDisable(disabled);
    }

    private void hideHints() {
        setHintHidden(lblAllowanceHint);
        setHintHidden(lblBonusHint);
    }

    private void setHintHidden(Label lbl) {
        if (lbl == null) return;
        lbl.setText(""); lbl.setVisible(false); lbl.setManaged(false);
    }

    // ─────────────────────────────────────────────────────────
    // INPUT PARSERS
    // ─────────────────────────────────────────────────────────

    private int parseEmployeeId() {
        try {
            int id = Integer.parseInt(txtEmployeeId.getText().trim());
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException e) {
            showError("Lỗi nhập liệu", "Employee ID phải là số nguyên dương.");
            return -1;
        }
    }

    private LocalDate parseMonth() {
        LocalDate d = dpSalaryMonth.getValue();
        if (d == null) { showError("Lỗi nhập liệu", "Vui lòng chọn tháng lương."); return null; }
        return d;
    }

    private BigDecimal parseBaseSalary() {
        try {
            String cleaned = txtBaseSalary.getText().trim().replace(".", "").replace(",", "");
            BigDecimal b = new BigDecimal(cleaned);
            if (b.compareTo(BigDecimal.ZERO) <= 0) throw new NumberFormatException();
            return b;
        } catch (Exception e) {
            showError("Lỗi nhập liệu", "Lương cơ bản phải là số dương.");
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────
    // UTILITIES
    // ─────────────────────────────────────────────────────────

    private BigDecimal parseBDSafe(String text, BigDecimal def) {
        try {
            String cleaned = text.trim().replace(".", "").replace(",", "");
            return new BigDecimal(cleaned);
        } catch (Exception e) { return def; }
    }

    /** Format số dương bình thường. */
    private String fmt(BigDecimal val) {
        return val != null ? String.format("%,.0f ₫", val) : "—";
    }

    /**
     * Format có màu/ký hiệu cho cột Bonus / Deduction trong bảng.
     * @param isDeduction true → hiện dấu âm (deduction trừ vào lương)
     */
    private String fmtSigned(BigDecimal val, boolean isDeduction) {
        if (val == null || val.compareTo(BigDecimal.ZERO) == 0) return "—";
        String amount = String.format("%,.0f ₫", val);
        return isDeduction ? "-" + amount : "+" + amount;
    }

    private void setStatus(String msg, boolean isError) {
        if (lblSalaryStatus != null) {
            lblSalaryStatus.setText(msg);
            lblSalaryStatus.setStyle(isError ? "-fx-text-fill:#c62828;" : "-fx-text-fill:#37474f;");
        }
    }

    private void showError(String title, String msg) {
        setStatus("⚠ " + msg, true);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}