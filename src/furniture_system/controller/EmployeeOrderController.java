package furniture_system.controller;

import furniture_system.dao.CustomerDAO;
import furniture_system.dao.DeliveryAddressDAO;
import furniture_system.dao.ProductDAO;
import furniture_system.dao.PromotionDAO;
import furniture_system.model.*;
import furniture_system.service.OrderService;
import furniture_system.utils.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Employee – Order Management & Billing (4.10.1 – 4.10.3)
 */
public class EmployeeOrderController implements Initializable {

    // ── FXML ─────────────────────────────────────────────────────────────

    // "My Orders" table
    @FXML private TableView<Order>               tblMyOrders;
    @FXML private TableColumn<Order, Integer>    colOrderId;
    @FXML private TableColumn<Order, String>     colCustomer;
    @FXML private TableColumn<Order, String>     colDate;
    @FXML private TableColumn<Order, String>     colStatus;
    @FXML private TableColumn<Order, BigDecimal> colFinal;
    @FXML private TableColumn<Order, Void>       colActions;

    // Create Order form
    @FXML private VBox              pnlCreateOrder;
    @FXML private ComboBox<Customer> cbCustomer;
    @FXML private ComboBox<DeliveryAddress> cbAddress;
    @FXML private ComboBox<Promotion>       cbPromo;
    @FXML private TextArea          taNote;

    // Line items
    @FXML private ComboBox<Product> cbProduct;
    @FXML private TextField         tfQty;
    @FXML private Button            btnAddLine;
    @FXML private TableView<OrderLine>               tblDraftLines;
    @FXML private TableColumn<OrderLine, String>     colDraftProduct;
    @FXML private TableColumn<OrderLine, Integer>    colDraftQty;
    @FXML private TableColumn<OrderLine, BigDecimal> colDraftPrice;
    @FXML private TableColumn<OrderLine, BigDecimal> colDraftTotal;
    @FXML private TableColumn<OrderLine, Void>       colDraftRemove;

    @FXML private Label  lblSubTotal;
    @FXML private Label  lblDiscount;
    @FXML private Label  lblFinalTotal;
    @FXML private Button btnCreateOrder;

    // Order detail / billing panel
    @FXML private VBox    pnlDetail;
    @FXML private Label   lblDetailTitle;
    @FXML private TableView<OrderLine>               tblDetailLines;
    @FXML private TableColumn<OrderLine, String>     colDetailProduct;
    @FXML private TableColumn<OrderLine, Integer>    colDetailQty;
    @FXML private TableColumn<OrderLine, BigDecimal> colDetailPrice;
    @FXML private TableColumn<OrderLine, BigDecimal> colDetailTotal;

    @FXML private ComboBox<String> cbUpdateStatus;
    @FXML private Button           btnUpdateStatus;

    // Billing
    @FXML private VBox             pnlBilling;
    @FXML private Label            lblBillingInfo;
    @FXML private ComboBox<String> cbPayMethod;
    @FXML private TextField        tfPaidAmount;
    @FXML private ComboBox<String> cbBillingStatus;
    @FXML private TextArea         taBillingNote;
    @FXML private Button           btnSaveBilling;

    // ── Sub-controller từ fx:include ──────────────────────────────────────
    // JavaFX inject theo quy tắc: fx:id="deliveryAddress" → field tên "deliveryAddressController"
    // Khai báo cả VBox wrapper (để show/hide) lẫn controller của nó
    @FXML private VBox                              deliveryAddress;           // node của fx:include
    @FXML private EmployeeDeliveryAddressController deliveryAddressController; // controller tương ứng

    // ── Fields ───────────────────────────────────────────────────────────

    private final OrderService       orderService = new OrderService();
    private final CustomerDAO        customerDAO  = new CustomerDAO();
    private final DeliveryAddressDAO addressDAO   = new DeliveryAddressDAO();
    private final ProductDAO         productDAO   = new ProductDAO();
    private final PromotionDAO       promoDAO     = new PromotionDAO();

    private final ObservableList<Order>     myOrders   = FXCollections.observableArrayList();
    private final ObservableList<OrderLine> draftLines = FXCollections.observableArrayList();
    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private Order selectedOrder;

    // ─────────────────────────────────────────────────────────────────────
    //  INIT
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupMyOrdersTable();
        setupDraftLinesTable();
        setupDetailTable();
        loadComboBoxes();
        loadMyOrders();

        pnlCreateOrder.setVisible(false);
        pnlDetail.setVisible(false);
        pnlBilling.setVisible(false);

        // Khi chọn khách hàng:
        //  1. Load địa chỉ vào cbAddress (ComboBox nhanh)
        //  2. Load EmployeeDeliveryAddressController để hiện bảng địa chỉ đầy đủ
        cbCustomer.valueProperty().addListener((obs, old, cust) -> {
            cbAddress.setValue(null);
            if (cust != null) {
                loadAddresses(cust.getCustomerId());
                linkDeliveryAddressPanel(cust.getCustomerId());
            } else {
                cbAddress.setItems(FXCollections.emptyObservableList());
                if (deliveryAddressController != null) {
                    deliveryAddressController.loadForCustomer(-1);
                }
            }
        });

        cbPromo.valueProperty().addListener((obs, o, p) -> recalcDraftTotals());

        tblMyOrders.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> onOrderSelected(sel));

        // Kết nối callback: khi emp chọn địa chỉ từ bảng → tự điền vào cbAddress
        if (deliveryAddressController != null) {
            deliveryAddressController.setOnAddressSelected(addr -> {
                // Đảm bảo addr nằm trong list cbAddress, nếu không thì thêm vào
                ObservableList<DeliveryAddress> items = cbAddress.getItems();
                boolean found = items.stream()
                        .anyMatch(a -> a.getAddressId() == addr.getAddressId());
                if (!found) items.add(addr);
                cbAddress.setValue(addr);
            });
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  LINK DELIVERY ADDRESS PANEL
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Gọi EmployeeDeliveryAddressController để load địa chỉ theo customerId.
     * Đồng thời đăng ký callback: khi emp bấm "Use This Address"
     * → cbAddress tự chọn đúng địa chỉ đó.
     */
    private void linkDeliveryAddressPanel(int customerId) {
        if (deliveryAddressController == null) {
            // fx:include chưa được khai báo trong FXML — fallback dùng cbAddress thôi
            return;
        }

        // Load bảng địa chỉ theo customer
        deliveryAddressController.loadForCustomer(customerId);

        // Callback: chọn địa chỉ từ bảng → điền vào cbAddress
        deliveryAddressController.setOnAddressSelected(addr -> {
            ObservableList<DeliveryAddress> items = cbAddress.getItems();
            boolean found = items.stream()
                    .anyMatch(a -> a.getAddressId() == addr.getAddressId());
            if (!found) items.add(addr);
            cbAddress.setValue(addr);
        });

        // Hiện panel địa chỉ (nếu đang ẩn)
        if (deliveryAddress != null) {
            deliveryAddress.setVisible(true);
            deliveryAddress.setManaged(true);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  TABLE SETUP
    // ─────────────────────────────────────────────────────────────────────

    private void setupMyOrdersTable() {
        colOrderId.setCellValueFactory(new PropertyValueFactory<>("orderId"));
        colCustomer.setCellValueFactory(new PropertyValueFactory<>("customerName"));
        colDate.setCellValueFactory(cd ->
                new SimpleStringProperty(
                        cd.getValue().getOrderDate() != null
                                ? cd.getValue().getOrderDate().format(dtf) : ""));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colFinal.setCellValueFactory(new PropertyValueFactory<>("finalTotal"));

        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Chi tiết");
            {   btn.getStyleClass().add("btn-primary-sm");
                btn.setOnAction(e -> tblMyOrders.getSelectionModel()
                        .select(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : btn);
            }
        });

        tblMyOrders.setItems(myOrders);
    }

    private void setupDraftLinesTable() {
        colDraftProduct.setCellValueFactory(new PropertyValueFactory<>("productName"));
        colDraftQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colDraftPrice.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));
        colDraftTotal.setCellValueFactory(new PropertyValueFactory<>("lineTotal"));
        colDraftRemove.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Xoá");
            {   btn.getStyleClass().add("btn-danger-sm");
                btn.setOnAction(e -> {
                    draftLines.remove(getTableView().getItems().get(getIndex()));
                    recalcDraftTotals();
                });
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty); setGraphic(empty ? null : btn);
            }
        });
        tblDraftLines.setItems(draftLines);
    }

    private void setupDetailTable() {
        colDetailProduct.setCellValueFactory(new PropertyValueFactory<>("productName"));
        colDetailQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colDetailPrice.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));
        colDetailTotal.setCellValueFactory(new PropertyValueFactory<>("lineTotal"));
    }

    // ─────────────────────────────────────────────────────────────────────
    //  LOAD DATA
    // ─────────────────────────────────────────────────────────────────────

    private void loadMyOrders() {
        try {
            int empId = getActorId();
            myOrders.setAll(orderService.getOrdersByEmployee(empId));
        } catch (Exception e) {
            showError("Lỗi tải đơn hàng", e.getMessage());
        }
    }

    private void loadComboBoxes() {
        try {
            cbCustomer.setItems(FXCollections.observableArrayList(customerDAO.findAll()));
            cbPromo.setItems(FXCollections.observableArrayList(promoDAO.findActive()));
            cbProduct.setItems(FXCollections.observableArrayList(productDAO.getActive()));
            cbUpdateStatus.setItems(FXCollections.observableArrayList(
                    "CONFIRMED", "PAID", "DELIVERING", "COMPLETED"));
            cbPayMethod.setItems(FXCollections.observableArrayList(
                    "CASH", "BANK_TRANSFER", "CARD", "OTHER"));
            cbBillingStatus.setItems(FXCollections.observableArrayList(
                    "UNPAID", "PARTIAL", "PAID"));
        } catch (Exception e) {
            showError("Lỗi tải dữ liệu", e.getMessage());
        }
    }

    /**
     * Load địa chỉ ACTIVE vào cbAddress.
     * Nếu không có địa chỉ nào → hiển thị cảnh báo cho user biết.
     */
    private void loadAddresses(int customerId) {
        try {
            List<DeliveryAddress> addrs = addressDAO.findByCustomerId(customerId);
            cbAddress.setItems(FXCollections.observableArrayList(addrs));

            if (addrs.isEmpty()) {
                // Không có địa chỉ ACTIVE — hiện thông báo nhẹ nhàng
                cbAddress.setPromptText("Khách hàng chưa có địa chỉ ACTIVE");
            } else {
                cbAddress.setPromptText("Chọn địa chỉ giao hàng");
                // Tự chọn địa chỉ mặc định nếu có
                addrs.stream()
                        .filter(DeliveryAddress::isDefault)
                        .findFirst()
                        .ifPresent(cbAddress::setValue);
            }
        } catch (Exception e) {
            showError("Lỗi tải địa chỉ", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  SHOW CREATE FORM
    // ─────────────────────────────────────────────────────────────────────

    @FXML
    private void onShowCreateOrder() {
        draftLines.clear();
        cbCustomer.setValue(null);
        cbAddress.setValue(null);
        cbPromo.setValue(null);
        taNote.clear();
        recalcDraftTotals();
        pnlDetail.setVisible(false);
        pnlCreateOrder.setVisible(true);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  ADD LINE TO DRAFT  (4.10.1)
    // ─────────────────────────────────────────────────────────────────────

    @FXML
    private void onAddLine() {
        Product p = cbProduct.getValue();
        if (p == null) { showWarning("Chọn sản phẩm."); return; }
        try {
            int qty = Integer.parseInt(tfQty.getText().trim());
            if (qty <= 0) throw new NumberFormatException();

            boolean dup = draftLines.stream().anyMatch(l -> l.getProductId() == p.getProductId());
            if (dup) { showWarning("Sản phẩm này đã có trong đơn."); return; }

            OrderLine line = new OrderLine(0, p.getProductId(), qty, p.getPrice());
            line.setProductName(p.getName());
            draftLines.add(line);
            recalcDraftTotals();
            tfQty.clear();
        } catch (NumberFormatException e) {
            showWarning("Số lượng không hợp lệ (phải là số nguyên > 0).");
        }
    }

    private void recalcDraftTotals() {
        BigDecimal sub = draftLines.stream()
                .map(l -> l.getUnitPrice().multiply(BigDecimal.valueOf(l.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal disc = BigDecimal.ZERO;
        Promotion promo = cbPromo.getValue();
        if (promo != null && sub.compareTo(promo.getMinOrderValue()) >= 0) {
            if ("PERCENT".equals(promo.getDiscountType())) {
                disc = sub.multiply(promo.getDiscountValue())
                          .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
            } else {
                disc = promo.getDiscountValue().min(sub);
            }
        }

        BigDecimal fin = sub.subtract(disc).max(BigDecimal.ZERO);
        lblSubTotal.setText(formatMoney(sub));
        lblDiscount.setText(formatMoney(disc));
        lblFinalTotal.setText(formatMoney(fin));
    }

    // ─────────────────────────────────────────────────────────────────────
    //  CREATE ORDER  (4.10.1)
    // ─────────────────────────────────────────────────────────────────────

    @FXML
    private void onCreateOrder() {
        Customer cust = cbCustomer.getValue();
        DeliveryAddress addr = cbAddress.getValue();

        if (cust == null)         { showWarning("Chọn khách hàng."); return; }
        if (addr == null)         { showWarning("Chọn địa chỉ giao hàng."); return; }
        if (draftLines.isEmpty()) { showWarning("Thêm ít nhất một sản phẩm."); return; }

        try {
            int empId = getActorId();
            Promotion promo = cbPromo.getValue();

            Order order = new Order(
                    cust.getCustomerId(), empId, addr.getAddressId(),
                    promo != null ? promo.getPromoId() : null,
                    taNote.getText().trim().isBlank() ? null : taNote.getText().trim()
            );

            List<OrderLine> lines = new ArrayList<>(draftLines);
            int newId = orderService.createOrder(order, lines);
            showInfo("Tạo đơn hàng thành công! Mã đơn: #" + newId);
            pnlCreateOrder.setVisible(false);
            loadMyOrders();
        } catch (Exception e) {
            showError("Lỗi tạo đơn hàng", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  ORDER SELECTION & DETAIL
    // ─────────────────────────────────────────────────────────────────────

    private void onOrderSelected(Order order) {
        if (order == null) return;
        selectedOrder = order;
        try {
            lblDetailTitle.setText("Đơn #" + order.getOrderId()
                    + " – " + order.getCustomerName()
                    + " [" + order.getStatus() + "]");
            List<OrderLine> lines = orderService.getLinesForOrder(order.getOrderId());
            tblDetailLines.setItems(FXCollections.observableArrayList(lines));
            cbUpdateStatus.setValue(null);
            pnlCreateOrder.setVisible(false);
            pnlDetail.setVisible(true);
            loadBillingPanel(order);
        } catch (Exception e) {
            showError("Lỗi tải chi tiết", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  UPDATE STATUS  (4.10.2)
    // ─────────────────────────────────────────────────────────────────────

    @FXML
    private void onUpdateStatus() {
        if (selectedOrder == null) return;
        String newStatus = cbUpdateStatus.getValue();
        if (newStatus == null) { showWarning("Chọn trạng thái mới."); return; }
        try {
            int actorId = getActorId();
            orderService.updateStatus(selectedOrder.getOrderId(), newStatus, actorId, null);
            showInfo("Cập nhật trạng thái → " + newStatus);
            loadMyOrders();
            selectedOrder = orderService.getOrderById(selectedOrder.getOrderId());
            onOrderSelected(selectedOrder);
        } catch (Exception e) {
            showError("Lỗi cập nhật trạng thái", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  BILLING  (4.10.3)
    // ─────────────────────────────────────────────────────────────────────

    private void loadBillingPanel(Order order) {
        try {
            Billing billing = orderService.getBillingByOrder(order.getOrderId());
            if (billing != null) {
                lblBillingInfo.setText("Hoá đơn #" + billing.getInvoiceId()
                        + " | " + billing.getBillingStatus()
                        + " | Đã thu: " + billing.getPaidAmount());
                cbPayMethod.setValue(billing.getPaymentMethod());
                tfPaidAmount.setText(billing.getPaidAmount().toPlainString());
                cbBillingStatus.setValue(billing.getBillingStatus());
                taBillingNote.setText(billing.getNote());
                btnSaveBilling.setText("Cập nhật hoá đơn");
            } else {
                lblBillingInfo.setText("Chưa có hoá đơn");
                cbPayMethod.setValue("CASH");
                tfPaidAmount.setText("0");
                cbBillingStatus.setValue("UNPAID");
                taBillingNote.clear();
                btnSaveBilling.setText("Tạo hoá đơn");
            }
            pnlBilling.setVisible(true);
        } catch (Exception e) {
            showError("Lỗi tải billing", e.getMessage());
        }
    }

    @FXML
    private void onSaveBilling() {
        if (selectedOrder == null) return;
        try {
            BigDecimal paidAmount = new BigDecimal(tfPaidAmount.getText().trim());
            Billing existing = orderService.getBillingByOrder(selectedOrder.getOrderId());

            if (existing == null) {
                Billing b = new Billing(selectedOrder.getOrderId(),
                        cbPayMethod.getValue(), paidAmount,
                        cbBillingStatus.getValue(),
                        taBillingNote.getText().trim().isBlank() ? null : taBillingNote.getText().trim());
                orderService.createBilling(b);
                showInfo("Hoá đơn đã được tạo.");
            } else {
                existing.setPaymentMethod(cbPayMethod.getValue());
                existing.setPaidAmount(paidAmount);
                existing.setBillingStatus(cbBillingStatus.getValue());
                existing.setNote(taBillingNote.getText().trim().isBlank() ? null : taBillingNote.getText().trim());
                orderService.updateBilling(existing, false);
                showInfo("Hoá đơn đã được cập nhật.");
            }
            loadBillingPanel(selectedOrder);
        } catch (NumberFormatException e) {
            showWarning("Số tiền không hợp lệ.");
        } catch (Exception e) {
            showError("Lỗi lưu hoá đơn", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  UTILITY
    // ─────────────────────────────────────────────────────────────────────

    private int getActorId() {
        Employee emp = SessionManager.getInstance().getCurrentEmployee();
        if (emp == null)
            throw new IllegalStateException("Phiên đăng nhập không hợp lệ – không tìm thấy thông tin nhân viên.");
        return emp.getEmployeeId();
    }

    private String formatMoney(BigDecimal v) {
        return String.format("%,.0f ₫", v);
    }

    private void showError(String header, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setHeaderText(header); a.setContentText(msg); a.showAndWait();
    }
    private void showWarning(String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }
    private void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }
}