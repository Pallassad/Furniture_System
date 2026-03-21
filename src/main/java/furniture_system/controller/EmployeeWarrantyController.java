package furniture_system.controller;

import furniture_system.dao.WarrantyTicketDAO;
import furniture_system.model.WarrantyTicket;
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

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

/**
 * Employee – Warranty / Repair Management.
 * Hiển thị TẤT CẢ ticket (chung với Admin).
 * Employee chỉ có thể tạo ticket mới và update status/cost/note.
 * setCurrentEmployeeId() được gọi từ EmployeeDashboardController sau khi load FXML.
 */
public class EmployeeWarrantyController {

    // ── Table ──────────────────────────────────────────────────────────────
    @FXML private TableView<WarrantyTicket>            tableWarranty;
    @FXML private TableColumn<WarrantyTicket, String>  colTicketId;
    @FXML private TableColumn<WarrantyTicket, String>  colOrderId;
    @FXML private TableColumn<WarrantyTicket, String>  colProduct;
    @FXML private TableColumn<WarrantyTicket, String>  colCustomer;
    @FXML private TableColumn<WarrantyTicket, String>  colStatus;
    @FXML private TableColumn<WarrantyTicket, String>  colCost;
    @FXML private TableColumn<WarrantyTicket, String>  colCreatedAt;

    // ── Toolbar ────────────────────────────────────────────────────────────
    @FXML private Button btnCreate;
    @FXML private Button btnUpdate;
    @FXML private Label  lblEmpStatus;

    private static final List<String> UPDATE_STATUSES = List.of(
            "RECEIVED", "PROCESSING", "WAITING_PART", "COMPLETED", "REJECTED", "CANCELLED");

    private final WarrantyTicketDAO          dao  = new WarrantyTicketDAO();
    private final ObservableList<WarrantyTicket> data = FXCollections.observableArrayList();
    private int currentEmployeeId = -1;

    // ── Dependency injection từ EmployeeDashboardController ───────────────
    public void setCurrentEmployeeId(int id) {
        this.currentEmployeeId = id;
        loadAll(); // load sau khi có employeeId
    }

    // ══════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        setupTable();

        tableWarranty.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> {
                    boolean canUpdate = sel != null &&
                            !List.of("COMPLETED","CANCELLED","REJECTED").contains(sel.getStatus());
                    btnUpdate.setDisable(!canUpdate);
                });
        btnUpdate.setDisable(true);

        // Double-click → open update dialog
        tableWarranty.setRowFactory(tv -> {
            TableRow<WarrantyTicket> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) openUpdateDialog(row.getItem());
            });
            return row;
        });

        // Fallback: nếu setCurrentEmployeeId() chưa được gọi thì tự lấy từ SessionManager
        if (currentEmployeeId <= 0) {
            var emp = SessionManager.getInstance().getCurrentEmployee();
            if (emp != null) {
                currentEmployeeId = emp.getEmployeeId();
            }
        }

        // Load tất cả ticket ngay khi initialize (nếu đã có employeeId)
        if (currentEmployeeId > 0) {
            loadAll();
        }
    }

    // ── Table setup ────────────────────────────────────────────────────────
    private void setupTable() {
        colTicketId .setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getTicketId())));
        colOrderId  .setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getOrderId())));
        colProduct  .setCellValueFactory(c -> new SimpleStringProperty(nvl(c.getValue().getProductName())));
        colCustomer .setCellValueFactory(c -> new SimpleStringProperty(nvl(c.getValue().getCustomerName())));
        colStatus   .setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus()));
        colCost     .setCellValueFactory(c -> {
            BigDecimal cost = c.getValue().getCost();
            return new SimpleStringProperty(cost != null ? String.format("%,.0f ₫", cost) : "0 ₫");
        });
        colCreatedAt.setCellValueFactory(c -> {
            var dt = c.getValue().getCreatedAt();
            return new SimpleStringProperty(dt != null ? dt.toLocalDate().toString() : "—");
        });

        // Status color
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); setStyle(""); return; }
                setText(s);
                setStyle(switch (s) {
                    case "COMPLETED"   -> "-fx-text-fill:#27ae60;-fx-font-weight:bold;";
                    case "CANCELLED",
                         "REJECTED"   -> "-fx-text-fill:#c62828;-fx-font-weight:bold;";
                    case "PROCESSING" -> "-fx-text-fill:#e65100;-fx-font-weight:bold;";
                    case "RECEIVED"   -> "-fx-text-fill:#1565c0;-fx-font-weight:bold;";
                    default           -> "-fx-text-fill:#555;";
                });
            }
        });

        // Row highlight
        tableWarranty.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(WarrantyTicket item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setStyle(""); return; }
                if ("CANCELLED".equals(item.getStatus()) || "REJECTED".equals(item.getStatus()))
                    setStyle("-fx-background-color:#e8e8e8;");
                else if ("COMPLETED".equals(item.getStatus()))
                    setStyle("-fx-background-color:#e8f5e9;");
                else setStyle("");
            }
        });

        tableWarranty.setItems(data);
        tableWarranty.setPlaceholder(new Label("No warranty tickets found."));
    }

    // ── Load TẤT CẢ ticket (hiển thị chung với Admin) ─────────────────────
    private void loadAll() {
        try {
            data.setAll(dao.getAll());
            setStatus(data.size() + " ticket(s).", false);
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Error", e.getMessage());
        }
    }

    @FXML public void handleRefresh() { loadAll(); }

    // ==================== CREATE TICKET DIALOG ====================
    @FXML public void handleCreate() {
        // Fallback: lấy lại từ SessionManager nếu chưa có
        if (currentEmployeeId <= 0) {
            var emp = SessionManager.getInstance().getCurrentEmployee();
            if (emp != null) currentEmployeeId = emp.getEmployeeId();
        }

        if (currentEmployeeId <= 0) {
            alert(Alert.AlertType.ERROR, "Error",
                  "Cannot determine current employee. Please re-login.");
            return;
        }

        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Create New Warranty Ticket");

        TextField tfOrderId    = new TextField();
        TextField tfProductId  = new TextField();
        TextField tfCustomerId = new TextField();
        TextArea  taIssue      = new TextArea();
        TextArea  taNote       = new TextArea();

        tfOrderId.setPromptText("Completed order ID");
        tfProductId.setPromptText("Product ID");
        tfCustomerId.setPromptText("Customer ID");
        taIssue.setPrefRowCount(4); taIssue.setWrapText(true);
        taNote.setPrefRowCount(3);  taNote.setWrapText(true);

        Label lblErr = new Label();
        lblErr.setStyle("-fx-text-fill:#c62828;-fx-font-size:12px;"); lblErr.setWrapText(true);

        GridPane grid = buildGrid();
        int row = 0;
        grid.add(sec("-- Order & Product Info"),   0, row, 2, 1); row++;
        grid.add(fl("Order ID *"),      0, row); grid.add(tfOrderId,    1, row++);
        grid.add(fl("Product ID *"),    0, row); grid.add(tfProductId,  1, row++);
        grid.add(fl("Customer ID *"),   0, row); grid.add(tfCustomerId, 1, row++);
        grid.add(new Label(""),          0, row); grid.add(hint("You will be auto-assigned as handler."), 1, row++);
        grid.add(new Separator(),                  0, row, 2, 1); row++;
        grid.add(sec("-- Issue Details"),          0, row, 2, 1); row++;
        grid.add(fl("Issue Desc *"),    0, row); grid.add(taIssue,      1, row++);
        grid.add(fl("Note"),            0, row); grid.add(taNote,       1, row++);
        grid.add(new Separator(),                  0, row, 2, 1); row++;
        grid.add(lblErr,                           0, row, 2, 1); row++;

        Button btnSave   = primaryBtn("Create Ticket");
        Button btnCancel = new Button("Cancel");
        btnCancel.setStyle("-fx-background-radius:6;-fx-padding:8 18;");
        HBox btns = new HBox(10, btnCancel, btnSave); btns.setAlignment(Pos.CENTER_RIGHT);
        grid.add(btns, 0, row, 2, 1);

        btnSave.setOnAction(ev -> {
            lblErr.setText("");
            try {
                int orderId    = Integer.parseInt(tfOrderId.getText().trim());
                int productId  = Integer.parseInt(tfProductId.getText().trim());
                int customerId = Integer.parseInt(tfCustomerId.getText().trim());
                String issueDesc = taIssue.getText().trim();
                if (issueDesc.isEmpty()) { lblErr.setText("Issue Description is required."); return; }
                String note = taNote.getText().trim();

                WarrantyTicket t = new WarrantyTicket();
                t.setOrderId(orderId);
                t.setProductId(productId);
                t.setCustomerId(customerId);
                t.setHandlerEmployeeId(currentEmployeeId);
                t.setIssueDesc(issueDesc);
                t.setStatus("CREATED");
                t.setCost(BigDecimal.ZERO);
                t.setNote(note.isEmpty() ? null : note);

                int id = dao.insert(t);
                setStatus("Ticket #" + id + " created.", false);
                loadAll();
                dlg.close();
            } catch (NumberFormatException ex) {
                lblErr.setText("Please enter valid numeric IDs.");
            } catch (SQLException ex) {
                lblErr.setText("DB Error: " + ex.getMessage());
            }
        });
        btnCancel.setOnAction(ev -> dlg.close());

        dlg.setScene(new Scene(grid, 480, 480));
        dlg.setResizable(false);
        dlg.showAndWait();
    }

    // ==================== UPDATE DIALOG ====================
    @FXML public void handleUpdate() {
        WarrantyTicket sel = tableWarranty.getSelectionModel().getSelectedItem();
        if (sel != null) openUpdateDialog(sel);
    }

    private void openUpdateDialog(WarrantyTicket sel) {
        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Update Ticket #" + sel.getTicketId());

        ComboBox<String> cbStatus = new ComboBox<>(FXCollections.observableArrayList(UPDATE_STATUSES));
        TextField        tfCost   = new TextField(sel.getCost() != null ? sel.getCost().toPlainString() : "0");
        TextArea         taNote   = new TextArea(sel.getNote() != null ? sel.getNote() : "");
        cbStatus.setValue(sel.getStatus()); cbStatus.setMaxWidth(Double.MAX_VALUE);
        taNote.setPrefRowCount(3); taNote.setWrapText(true);
        tfCost.setPromptText("0 = free warranty");

        Label lblInfo = new Label("Ticket #" + sel.getTicketId()
                + "   |   " + nvl(sel.getProductName())
                + "   |   Customer: " + nvl(sel.getCustomerName()));
        lblInfo.setStyle("-fx-text-fill:#555;-fx-font-size:12px;"); lblInfo.setWrapText(true);

        Label lblCurrent = new Label("Current status: " + sel.getStatus());
        lblCurrent.setStyle("-fx-font-size:12px;-fx-text-fill:#3949ab;-fx-font-weight:bold;");

        Label lblErr = new Label();
        lblErr.setStyle("-fx-text-fill:#c62828;-fx-font-size:12px;"); lblErr.setWrapText(true);

        GridPane grid = buildGrid();
        int row = 0;
        grid.add(lblInfo,                    0, row, 2, 1); row++;
        grid.add(lblCurrent,                 0, row, 2, 1); row++;
        grid.add(new Separator(),            0, row, 2, 1); row++;
        grid.add(sec("-- Update Details"),   0, row, 2, 1); row++;
        grid.add(fl("New Status *"), 0, row); grid.add(cbStatus, 1, row++);
        grid.add(fl("Cost (₫)"),     0, row); grid.add(tfCost,   1, row++);
        grid.add(new Label(""),       0, row); grid.add(hint("0 = free warranty  |  > 0 = chargeable repair"), 1, row++);
        grid.add(fl("Note"),         0, row); grid.add(taNote,   1, row++);
        grid.add(new Separator(),            0, row, 2, 1); row++;
        grid.add(lblErr,                     0, row, 2, 1); row++;

        Button btnSave   = primaryBtn("Update Ticket");
        Button btnCancel = new Button("Cancel");
        btnCancel.setStyle("-fx-background-radius:6;-fx-padding:8 18;");
        HBox btns = new HBox(10, btnCancel, btnSave); btns.setAlignment(Pos.CENTER_RIGHT);
        grid.add(btns, 0, row, 2, 1);

        btnSave.setOnAction(ev -> {
            lblErr.setText("");
            String status = cbStatus.getValue();
            if (status == null) { lblErr.setText("Status is required."); return; }
            try {
                BigDecimal cost = new BigDecimal(tfCost.getText().trim().isEmpty() ? "0" : tfCost.getText().trim());
                if (cost.compareTo(BigDecimal.ZERO) < 0) { lblErr.setText("Cost must be ≥ 0."); return; }
                String note = taNote.getText().trim();
                dao.updateStatusCostNote(sel.getTicketId(), status, cost, note.isEmpty() ? null : note);
                setStatus("Ticket #" + sel.getTicketId() + " → " + status, false);
                loadAll();
                dlg.close();
            } catch (NumberFormatException ex) {
                lblErr.setText("Cost must be a valid number.");
            } catch (SQLException ex) {
                lblErr.setText("DB Error: " + ex.getMessage());
            }
        });
        btnCancel.setOnAction(ev -> dlg.close());

        dlg.setScene(new Scene(grid, 480, 400));
        dlg.setResizable(false);
        dlg.showAndWait();
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
    private void setStatus(String msg, boolean isError) {
        if (lblEmpStatus == null) return;
        lblEmpStatus.setText(msg);
        lblEmpStatus.setStyle(isError ? "-fx-text-fill:#c62828;-fx-font-size:12px;"
                                      : "-fx-text-fill:#37474f;-fx-font-size:12px;");
    }
    private void alert(Alert.AlertType t, String title, String msg) {
        Alert a = new Alert(t); a.setTitle(title); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }
    private static String nvl(String s) { return s != null ? s : "—"; }
}