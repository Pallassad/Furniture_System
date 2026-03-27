package furniture_system.controller;

import furniture_system.service.WarrantyTicketService;
import furniture_system.model.WarrantyTicket;
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
import java.util.Set;

/**
 * FIX: Bỏ hoàn toàn WarrantyTicketDAO trực tiếp.
 * Tất cả operations đều đi qua WarrantyTicketService để enforce:
 *   - Status transition hợp lệ
 *   - Terminal state guard
 *   - Business validation (issueDesc, cost)
 */
public class AdminWarrantyManagementController {

    // ── Table ──────────────────────────────────────────────────────────────
    @FXML private TableView<WarrantyTicket>            tableWarranty;
    @FXML private TableColumn<WarrantyTicket, String>  colTicketId;
    @FXML private TableColumn<WarrantyTicket, String>  colOrderId;
    @FXML private TableColumn<WarrantyTicket, String>  colProduct;
    @FXML private TableColumn<WarrantyTicket, String>  colCustomer;
    @FXML private TableColumn<WarrantyTicket, String>  colHandler;
    @FXML private TableColumn<WarrantyTicket, String>  colStatus;
    @FXML private TableColumn<WarrantyTicket, String>  colCost;
    @FXML private TableColumn<WarrantyTicket, String>  colCreatedAt;

    // ── Search & Toolbar ───────────────────────────────────────────────────
    @FXML private TextField txtSearch;
    @FXML private Button    btnAdd;
    @FXML private Button    btnEdit;
    @FXML private Button    btnDelete;
    @FXML private Label     lblStatus;

    private static final List<String> STATUSES = List.of(
            "CREATED", "RECEIVED", "PROCESSING", "WAITING_PART",
            "COMPLETED", "REJECTED", "CANCELLED");

    private static final Set<String> TERMINAL_STATUSES =
            Set.of("COMPLETED", "CANCELLED", "REJECTED");

    // FIX: Chỉ dùng Service, không inject DAO trực tiếp
    private final WarrantyTicketService      warrantyService  = new WarrantyTicketService();
    private final ObservableList<WarrantyTicket> data = FXCollections.observableArrayList();

    // ══════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        setupTable();
        loadAll();

        tableWarranty.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> {
                    boolean has = sel != null;
                    btnEdit.setDisable(!has);
                    boolean canDelete = has &&
                            List.of("COMPLETED","CANCELLED","REJECTED").contains(sel.getStatus());
                    btnDelete.setDisable(!canDelete);
                });
        btnEdit.setDisable(true);
        btnDelete.setDisable(true);
    }

    // ── Table setup ────────────────────────────────────────────────────────
    private void setupTable() {
        colTicketId .setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getTicketId())));
        colOrderId  .setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getOrderId())));
        colProduct  .setCellValueFactory(c -> new SimpleStringProperty(nvl(c.getValue().getProductName())));
        colCustomer .setCellValueFactory(c -> new SimpleStringProperty(nvl(c.getValue().getCustomerName())));
        colHandler  .setCellValueFactory(c -> new SimpleStringProperty(nvl(c.getValue().getHandlerName())));
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
                    case "COMPLETED"    -> "-fx-text-fill:#27ae60;-fx-font-weight:bold;";
                    case "CANCELLED",
                         "REJECTED"    -> "-fx-text-fill:#c62828;-fx-font-weight:bold;";
                    case "PROCESSING"  -> "-fx-text-fill:#e65100;-fx-font-weight:bold;";
                    case "RECEIVED"    -> "-fx-text-fill:#1565c0;-fx-font-weight:bold;";
                    default            -> "-fx-text-fill:#555;";
                });
            }
        });

        // Row highlight for closed tickets
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

    // ── Load & Search ──────────────────────────────────────────────────────
    private void loadAll() {
        try {
            data.setAll(warrantyService.getAll());
            setStatus("Loaded " + data.size() + " ticket(s).", false);
        } catch (SQLException e) { alert(Alert.AlertType.ERROR, "Error", e.getMessage()); }
    }

    @FXML public void handleRefresh() { txtSearch.clear(); loadAll(); }

    @FXML public void handleSearch() {
        String kw = txtSearch.getText().trim();
        try {
            data.setAll(warrantyService.search(kw));
            setStatus(data.size() + " result(s).", false);
        } catch (SQLException e) { alert(Alert.AlertType.ERROR, "Search Error", e.getMessage()); }
    }

    // ==================== ADD DIALOG ====================
    @FXML public void handleAdd() {
        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Create New Warranty Ticket");

        TextField tfOrderId    = new TextField();
        TextField tfProductId  = new TextField();
        TextField tfCustomerId = new TextField();
        TextField tfHandlerId  = new TextField();
        TextArea  taIssue      = new TextArea();
        TextArea  taNote       = new TextArea();
        TextField tfCost       = new TextField("0");
        ComboBox<String> cbStatus = new ComboBox<>(FXCollections.observableArrayList(STATUSES));

        cbStatus.setValue("CREATED"); cbStatus.setMaxWidth(Double.MAX_VALUE);
        tfOrderId.setPromptText("e.g. 5"); tfProductId.setPromptText("e.g. 3");
        tfCustomerId.setPromptText("e.g. 1"); tfHandlerId.setPromptText("Optional");
        tfCost.setPromptText("0 = free warranty");
        taIssue.setPrefRowCount(4); taIssue.setWrapText(true);
        taNote.setPrefRowCount(3);  taNote.setWrapText(true);

        Label lblErr = new Label();
        lblErr.setStyle("-fx-text-fill:#c62828;-fx-font-size:12px;"); lblErr.setWrapText(true);

        GridPane grid = buildGrid();
        int row = 0;
        grid.add(sec("-- Order & Product Info"),  0, row, 2, 1); row++;
        grid.add(fl("Order ID *"),      0, row); grid.add(tfOrderId,    1, row++);
        grid.add(fl("Product ID *"),    0, row); grid.add(tfProductId,  1, row++);
        grid.add(fl("Customer ID *"),   0, row); grid.add(tfCustomerId, 1, row++);
        grid.add(fl("Handler Emp ID"),  0, row); grid.add(tfHandlerId,  1, row++);
        grid.add(new Label(""),          0, row); grid.add(hint("Optional. Leave blank to assign later."), 1, row++);
        grid.add(new Separator(),                 0, row, 2, 1); row++;
        grid.add(sec("-- Issue Details"),         0, row, 2, 1); row++;
        grid.add(fl("Issue Desc *"),    0, row); grid.add(taIssue,      1, row++);
        grid.add(fl("Note"),            0, row); grid.add(taNote,       1, row++);
        grid.add(new Separator(),                 0, row, 2, 1); row++;
        grid.add(sec("-- Status & Cost"),         0, row, 2, 1); row++;
        grid.add(fl("Status *"),        0, row); grid.add(cbStatus,     1, row++);
        grid.add(fl("Cost (₫)"),        0, row); grid.add(tfCost,       1, row++);
        grid.add(new Label(""),          0, row); grid.add(hint("0 = free warranty coverage."), 1, row++);
        grid.add(new Separator(),                 0, row, 2, 1); row++;
        grid.add(lblErr,                          0, row, 2, 1); row++;

        Button btnSave   = primaryBtn("Create Ticket");
        Button btnCancel = new Button("Cancel");
        btnCancel.setStyle("-fx-background-radius:6;-fx-padding:8 18;");
        HBox btns = new HBox(10, btnCancel, btnSave); btns.setAlignment(Pos.CENTER_RIGHT);
        grid.add(btns, 0, row, 2, 1);

        btnSave.setOnAction(ev -> {
            lblErr.setText("");
            WarrantyTicket t = buildTicket(null, tfOrderId, tfProductId, tfCustomerId,
                    tfHandlerId, taIssue, taNote, cbStatus, tfCost, lblErr);
            if (t == null) return;
            try {
                // FIX: Dùng service.create() để enforce validation
                // (status bị force CREATED, order phải COMPLETED, v.v.)
                int id = warrantyService.create(t);
                setStatus("Ticket #" + id + " created successfully.", false);
                loadAll(); dlg.close();
            } catch (IllegalArgumentException ex) {
                lblErr.setText(ex.getMessage());
            } catch (SQLException ex) { lblErr.setText("DB Error: " + ex.getMessage()); }
        });
        btnCancel.setOnAction(ev -> dlg.close());

        ScrollPane scroll = new ScrollPane(grid); scroll.setFitToWidth(true);
        dlg.setScene(new Scene(scroll, 500, 620)); dlg.showAndWait();
    }

    // ==================== EDIT DIALOG ====================
    @FXML public void handleEdit() {
        WarrantyTicket sel = tableWarranty.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Edit Ticket #" + sel.getTicketId());

        TextField tfOrderId    = new TextField(String.valueOf(sel.getOrderId()));
        TextField tfProductId  = new TextField(String.valueOf(sel.getProductId()));
        TextField tfCustomerId = new TextField(String.valueOf(sel.getCustomerId()));
        TextField tfHandlerId  = new TextField(sel.getHandlerEmployeeId() != null
                ? String.valueOf(sel.getHandlerEmployeeId()) : "");
        TextArea  taIssue      = new TextArea(nvl(sel.getIssueDesc()));
        TextArea  taNote       = new TextArea(sel.getNote() != null ? sel.getNote() : "");
        TextField tfCost       = new TextField(sel.getCost() != null ? sel.getCost().toPlainString() : "0");
        ComboBox<String> cbStatus = new ComboBox<>(FXCollections.observableArrayList(STATUSES));
        cbStatus.setValue(sel.getStatus()); cbStatus.setMaxWidth(Double.MAX_VALUE);
        taIssue.setPrefRowCount(4); taIssue.setWrapText(true);
        taNote.setPrefRowCount(3);  taNote.setWrapText(true);

        Label lblInfo = new Label("Ticket #" + sel.getTicketId()
                + "   |   Created: " + (sel.getCreatedAt() != null ? sel.getCreatedAt().toLocalDate() : "—"));
        lblInfo.setStyle("-fx-text-fill:#555;-fx-font-size:12px;");
        Label lblErr = new Label();
        lblErr.setStyle("-fx-text-fill:#c62828;-fx-font-size:12px;"); lblErr.setWrapText(true);

        GridPane grid = buildGrid();
        int row = 0;
        grid.add(lblInfo,                         0, row, 2, 1); row++;
        grid.add(new Separator(),                 0, row, 2, 1); row++;
        grid.add(sec("-- Order & Product Info"),  0, row, 2, 1); row++;
        grid.add(fl("Order ID *"),      0, row); grid.add(tfOrderId,    1, row++);
        grid.add(fl("Product ID *"),    0, row); grid.add(tfProductId,  1, row++);
        grid.add(fl("Customer ID *"),   0, row); grid.add(tfCustomerId, 1, row++);
        grid.add(fl("Handler Emp ID"),  0, row); grid.add(tfHandlerId,  1, row++);
        grid.add(new Separator(),                 0, row, 2, 1); row++;
        grid.add(sec("-- Issue Details"),         0, row, 2, 1); row++;
        grid.add(fl("Issue Desc *"),    0, row); grid.add(taIssue,      1, row++);
        grid.add(fl("Note"),            0, row); grid.add(taNote,       1, row++);
        grid.add(new Separator(),                 0, row, 2, 1); row++;
        grid.add(sec("-- Status & Cost"),         0, row, 2, 1); row++;
        grid.add(fl("Status *"),        0, row); grid.add(cbStatus,     1, row++);
        grid.add(fl("Cost (₫)"),        0, row); grid.add(tfCost,       1, row++);
        grid.add(new Separator(),                 0, row, 2, 1); row++;
        grid.add(lblErr,                          0, row, 2, 1); row++;

        Button btnSave   = primaryBtn("Save Changes");
        Button btnCancel = new Button("Cancel");
        btnCancel.setStyle("-fx-background-radius:6;-fx-padding:8 18;");
        HBox btns = new HBox(10, btnCancel, btnSave); btns.setAlignment(Pos.CENTER_RIGHT);
        grid.add(btns, 0, row, 2, 1);

        btnSave.setOnAction(ev -> {
            lblErr.setText("");
            WarrantyTicket t = buildTicket(sel.getTicketId(), tfOrderId, tfProductId,
                    tfCustomerId, tfHandlerId, taIssue, taNote, cbStatus, tfCost, lblErr);
            if (t == null) return;
            try {
                // FIX: Dùng service.update() để enforce:
                //   - terminal state guard
                //   - status transition hợp lệ
                //   - validation issueDesc, cost
                warrantyService.update(t);
                setStatus("Ticket #" + sel.getTicketId() + " updated.", false);
                loadAll(); dlg.close();
            } catch (IllegalArgumentException | IllegalStateException ex) {
                lblErr.setText(ex.getMessage());
            } catch (SQLException ex) { lblErr.setText("DB Error: " + ex.getMessage()); }
        });
        btnCancel.setOnAction(ev -> dlg.close());

        ScrollPane scroll = new ScrollPane(grid); scroll.setFitToWidth(true);
        dlg.setScene(new Scene(scroll, 500, 620)); dlg.showAndWait();
    }

    // ==================== CANCEL/REJECT ====================
    @FXML public void handleCancelTicket() {
        WarrantyTicket sel = tableWarranty.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        if (TERMINAL_STATUSES.contains(sel.getStatus())) {
            alert(Alert.AlertType.WARNING, "Cannot Cancel",
                "Ticket #" + sel.getTicketId() + " is already " + sel.getStatus() + ".");
            return;
        }

        ChoiceDialog<String> dlg = new ChoiceDialog<>("CANCELLED", "CANCELLED", "REJECTED");
        dlg.setTitle("Cancel / Reject Ticket");
        dlg.setHeaderText("Ticket #" + sel.getTicketId() + " — " + nvl(sel.getProductName()));
        dlg.setContentText("Set status to:");
        dlg.showAndWait().ifPresent(choice -> {
            try {
                // FIX: Dùng service để enforce terminal state guard
                if ("CANCELLED".equals(choice)) warrantyService.cancel(sel.getTicketId());
                else                            warrantyService.reject(sel.getTicketId());
                setStatus("Ticket #" + sel.getTicketId() + " → " + choice, false);
                loadAll();
            } catch (IllegalStateException ex) {
                alert(Alert.AlertType.WARNING, "Cannot Update", ex.getMessage());
            } catch (Exception ex) { alert(Alert.AlertType.ERROR, "Error", ex.getMessage()); }
        });
    }

    // ==================== DELETE (hard-delete terminal tickets) ====================
    @FXML public void handleDelete() {
        WarrantyTicket sel = tableWarranty.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
            "Permanently delete warranty ticket #" + sel.getTicketId()
            + " [" + nvl(sel.getProductName()) + "]?\n\n"
            + "⚠ This action cannot be undone.",
            ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm warranty ticket deletion");
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.YES) return;
            try {
                warrantyService.delete(sel.getTicketId());
                setStatus("Deleted warranty ticket #" + sel.getTicketId() + ".", false);
                loadAll();
            } catch (Exception ex) {
                alert(Alert.AlertType.ERROR, "Cannot delete", ex.getMessage());
            }
        });
    }

    // ── Shared builder ─────────────────────────────────────────────────────
    private WarrantyTicket buildTicket(Integer ticketId,
                                        TextField tfOrderId, TextField tfProductId,
                                        TextField tfCustomerId, TextField tfHandlerId,
                                        TextArea taIssue, TextArea taNote,
                                        ComboBox<String> cbStatus, TextField tfCost,
                                        Label lblErr) {
        try {
            int orderId    = Integer.parseInt(tfOrderId.getText().trim());
            int productId  = Integer.parseInt(tfProductId.getText().trim());
            int customerId = Integer.parseInt(tfCustomerId.getText().trim());
            String handlerStr = tfHandlerId.getText().trim();
            Integer handlerId = handlerStr.isEmpty() ? null : Integer.parseInt(handlerStr);
            String issueDesc = taIssue.getText().trim();
            if (issueDesc.isEmpty()) { lblErr.setText("Issue Description is required."); return null; }
            if (cbStatus.getValue() == null) { lblErr.setText("Status is required."); return null; }
            BigDecimal cost = new BigDecimal(tfCost.getText().trim().isEmpty() ? "0" : tfCost.getText().trim());
            if (cost.compareTo(BigDecimal.ZERO) < 0) { lblErr.setText("Cost must be ≥ 0."); return null; }
            String note = taNote.getText().trim();

            WarrantyTicket t = new WarrantyTicket();
            if (ticketId != null) t.setTicketId(ticketId);
            t.setOrderId(orderId); t.setProductId(productId); t.setCustomerId(customerId);
            t.setHandlerEmployeeId(handlerId); t.setIssueDesc(issueDesc);
            t.setStatus(cbStatus.getValue()); t.setCost(cost);
            t.setNote(note.isEmpty() ? null : note);
            return t;
        } catch (NumberFormatException e) {
            lblErr.setText("Please enter valid numeric IDs and cost."); return null;
        }
    }

    // ── UI helpers ─────────────────────────────────────────────────────────
    private GridPane buildGrid() {
        GridPane g = new GridPane(); g.setHgap(12); g.setVgap(10); g.setPadding(new Insets(24));
        g.getColumnConstraints().addAll(new ColumnConstraints(145), new ColumnConstraints(300));
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
        if (lblStatus == null) return;
        lblStatus.setText(msg);
        lblStatus.setStyle(isError ? "-fx-text-fill:#c62828;-fx-font-size:12px;"
                                   : "-fx-text-fill:#37474f;-fx-font-size:12px;");
    }
    private void alert(Alert.AlertType t, String title, String msg) {
        Alert a = new Alert(t); a.setTitle(title); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }
    private static String nvl(String s) { return s != null ? s : "—"; }
}