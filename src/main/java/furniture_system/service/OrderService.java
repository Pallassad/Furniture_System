package furniture_system.service;

import furniture_system.config.DatabaseConfig;
import furniture_system.dao.*;
import furniture_system.model.*;
import furniture_system.dao.WarrantyTicketDAO;
import furniture_system.utils.SessionManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;


public class OrderService {

    private final OrderDAO        orderDAO        = new OrderDAO();
    private final OrderLineDAO    lineDAO         = new OrderLineDAO();
    private final BillingDAO      billingDAO      = new BillingDAO();
    private final PromotionDAO    promoDAO        = new PromotionDAO();
    private final StockDAO        stockDAO        = new StockDAO();

    // ─────────────────────────────────────────────────────────────────────
    //  CREATE ORDER  (3.11.2 / 4.10.1)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Creates an Order + its OrderLines in a single transaction.
     *
     * @param order     Order header (Status will be set to DRAFT automatically)
     * @param lines     Line items – UnitPrice must already be locked (current Product.Price)
     * @return          The generated OrderId
     */
    public int createOrder(Order order, List<OrderLine> lines) throws Exception {
        if (lines == null || lines.isEmpty())
            throw new IllegalArgumentException("Order must have at least one product.");

        for (OrderLine l : lines) {
            if (l.getQuantity() <= 0)
                throw new IllegalArgumentException("Quantity must be > 0 for product " + l.getProductId());
            if (l.getUnitPrice() == null || l.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0)
                throw new IllegalArgumentException("UnitPrice must be > 0 for product " + l.getProductId());
        }

        // Calculate SubTotal
        BigDecimal subTotal = lines.stream()
                .map(l -> l.getUnitPrice().multiply(BigDecimal.valueOf(l.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Apply promotion
        BigDecimal discount = BigDecimal.ZERO;
        if (order.getPromoId() != null) {
            Promotion promo = promoDAO.findById(order.getPromoId());
            discount = calcDiscount(promo, subTotal);
        }

        BigDecimal finalTotal = subTotal.subtract(discount).max(BigDecimal.ZERO);
        order.setSubTotal(subTotal);
        order.setDiscount(discount);
        order.setFinalTotal(finalTotal);
        order.setStatus("DRAFT");
        order.setOrderDate(LocalDateTime.now());

        // Persist in a manual transaction
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Insert Order
                int orderId = insertOrderTx(conn, order);
                order.setOrderId(orderId);

                // Insert OrderLines
                for (OrderLine line : lines) {
                    line.setOrderId(orderId);
                    insertLineTx(conn, line);
                }

                // NOTE: Promotion UsedCount is NOT incremented here (order is still DRAFT).
                // It will be incremented atomically when the order transitions to CONFIRMED.

                conn.commit();
                return orderId;
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  ADD / REMOVE LINES  (DRAFT only)
    // ─────────────────────────────────────────────────────────────────────

    public void addLine(int orderId, OrderLine line) throws Exception {
        Order order = requireOrder(orderId);
        requireDraft(order);
        line.setOrderId(orderId);
        lineDAO.insert(line);
        recalcTotals(order);
    }

    public void removeLine(int orderId, int lineId) throws Exception {
        Order order = requireOrder(orderId);
        requireDraft(order);
        lineDAO.delete(lineId);
        recalcTotals(order);
    }

    public void updateLine(int orderId, OrderLine line) throws Exception {
        Order order = requireOrder(orderId);
        requireDraft(order);
        if (line.getQuantity() <= 0)
            throw new IllegalArgumentException("Quantity must be > 0.");
        lineDAO.update(line);
        recalcTotals(order);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  UPDATE STATUS  (3.11.3 / 4.10.2)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Advances order status with all side effects.
     *
     * @param orderId   target order
     * @param newStatus target status
     * @param actorId   EmployeeId of the person making the change (for StockLog)
     * @param note      required for post-PAID admin adjustments
     */
    public void updateStatus(int orderId, String newStatus, int actorId, String note) throws Exception {
        Order order = requireOrder(orderId);
        String current = order.getStatus();

        validateTransition(current, newStatus);

        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // ── CONFIRMED: deduct stock + increment promo usage ──────
                if ("CONFIRMED".equals(newStatus)) {
                    List<OrderLine> lines = lineDAO.findByOrderId(orderId);
                    checkStock(lines);          // throws if insufficient
                    deductStock(conn, lines, orderId, actorId);
                    // Increment promotion UsedCount only when order is confirmed
                    if (order.getPromoId() != null)
                        incrementPromoTx(conn, order.getPromoId());
                }

                // ── CANCELLED / RETURNED: restore stock + refund promo ──
                if ("CANCELLED".equals(newStatus) || "RETURNED".equals(newStatus)) {
                    // Only restore if stock was previously deducted (CONFIRMED or later)
                    if (wasStockDeducted(current)) {
                        List<OrderLine> lines = lineDAO.findByOrderId(orderId);
                        restoreStock(conn, lines, orderId, actorId,
                                "CANCELLED".equals(newStatus) ? "CANCEL" : "RETURN");
                    }
                    // Decrement promotion UsedCount if it was incremented at CONFIRMED
                    // (i.e. stock had been deducted means promo was also counted)
                    if (order.getPromoId() != null && wasStockDeducted(current)) {
                        decrementPromoTx(conn, order.getPromoId());
                    }
                }

                // ── Update Order.Status ──────────────────────────────────
                updateStatusTx(conn, orderId, newStatus);

                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  CANCEL ORDER  (3.11.4)
    // ─────────────────────────────────────────────────────────────────────

    public void cancelOrder(int orderId, int actorId) throws Exception {
        Order order = requireOrder(orderId);
        if (!"DRAFT".equals(order.getStatus()) && !"CONFIRMED".equals(order.getStatus()))
            throw new IllegalStateException("Only DRAFT or CONFIRMED orders can be cancelled.");
        updateStatus(orderId, "CANCELLED", actorId, null);
    }

    // ────────────────────────────────────────────────��────────────────────
    //  MANAGE BILLING  (3.11.5 / 4.10.3)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Creates a Billing record for an order (one per order, enforced).
     */
    public int createBilling(Billing billing) throws Exception {
        // Guard: one billing per order
        if (billingDAO.findByOrderId(billing.getOrderId()) != null)
            throw new IllegalStateException("A billing record already exists for order #" + billing.getOrderId());

        if (billing.getPaidAmount().compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("PaidAmount must be >= 0.");

        billing.setIssueDate(LocalDateTime.now());
        return billingDAO.insert(billing);
    }

    /**
     * Updates an existing Billing record.
     * Validates allowed BillingStatus values based on caller role.
     *
     * @param isAdmin  if false, REFUNDED/VOID are forbidden
     */
    public void updateBilling(Billing billing, boolean isAdmin) throws Exception {
        String status = billing.getBillingStatus();
        if (!isAdmin && ("REFUNDED".equals(status) || "VOID".equals(status)))
            throw new SecurityException("Only Admin can set REFUNDED or VOID billing status.");

        if (billing.getPaidAmount().compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("PaidAmount must be >= 0.");

        if (!billingDAO.update(billing))
            throw new SQLException("Failed to update billing record.");
    }

    // ─────────────────────────────────────────────────────────────────────
    //  DELETE BILLING  (3.11.6)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Delete Billing record of an Order.
     * Only allowed when BillingStatus = UNPAID or VOID.
     */
    public void deleteBilling(int invoiceId) throws Exception {
        Billing billing = billingDAO.findById(invoiceId);
        if (billing == null)
            throw new IllegalArgumentException("Invoice #" + invoiceId + " does not exist.");
        String status = billing.getBillingStatus();
        if (!"UNPAID".equals(status) && !"VOID".equals(status))
            throw new IllegalStateException(
                "Can only delete invoices with UNPAID or VOID status. " +
                "Current status: " + status);
        if (!billingDAO.delete(invoiceId))
            throw new SQLException("Failed to delete invoice.");
    }

    // ─────────────────────────────────────────────────────────────────────
    //  DELETE ORDER  (3.11.7)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Hard delete an Order and all of its OrderLines.
     *
     * Conditions:
     *  - Status must be COMPLETED, CANCELLED, or RETURNED
     *  - No remaining Billing (already deleted) or Billing = VOID
     *  - No active WarrantyTickets (not in terminal state)
     */
    public void deleteOrder(int orderId) throws Exception {
        Order order = requireOrder(orderId);

        // 1. Check status
        String status = order.getStatus();
        if (!"COMPLETED".equals(status) && !"CANCELLED".equals(status) && !"RETURNED".equals(status))
            throw new IllegalStateException(
                "Can only delete orders with COMPLETED, CANCELLED, or RETURNED status. " +
                "Current status: " + status);

        // 2. Check Billing
        Billing billing = billingDAO.findByOrderId(orderId);
        if (billing != null) {
            String bs = billing.getBillingStatus();
            if (!"VOID".equals(bs))
                throw new IllegalStateException(
                    "Order still has an invoice (status: " + bs + "). " +
                    "Please delete or set the invoice to VOID first.");
        }

        // 3. Check active WarrantyTickets
        WarrantyTicketDAO warrantyDAO = new WarrantyTicketDAO();
        boolean hasActiveWarranty = warrantyDAO.getAll().stream()
            .filter(w -> w.getOrderId() == orderId)
            .anyMatch(w -> {
                String ws = w.getStatus();
                return !"COMPLETED".equals(ws) && !"CANCELLED".equals(ws) && !"REJECTED".equals(ws);
            });
        if (hasActiveWarranty)
            throw new IllegalStateException(
                "Order still has warranty tickets being processed. " +
                "Please complete or cancel all warranty tickets first.");

        // 4. Delete OrderLines + Order in transaction
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Delete OrderLines
                String delLines = "DELETE FROM OrderLine WHERE OrderId = ?";
                try (PreparedStatement ps = conn.prepareStatement(delLines)) {
                    ps.setInt(1, orderId);
                    ps.executeUpdate();
                }
                // Delete Billing if exists (VOID)
                if (billing != null) {
                    String delBilling = "DELETE FROM Billing WHERE OrderId = ?";
                    try (PreparedStatement ps = conn.prepareStatement(delBilling)) {
                        ps.setInt(1, orderId);
                        ps.executeUpdate();
                    }
                }
                // Delete terminal WarrantyTickets for this Order
                String delWarranty = "DELETE FROM WarrantyTicket WHERE OrderId = ? " +
                    "AND Status IN ('COMPLETED','CANCELLED','REJECTED')";
                try (PreparedStatement ps = conn.prepareStatement(delWarranty)) {
                    ps.setInt(1, orderId);
                    ps.executeUpdate();
                }
                // Delete Order
                String delOrder = "DELETE FROM [Order] WHERE OrderId = ?";
                try (PreparedStatement ps = conn.prepareStatement(delOrder)) {
                    ps.setInt(1, orderId);
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  QUERIES
    // ─────────────────────────────────────────────────────────────────────

    public List<Order> getAllOrders() throws SQLException {
        return orderDAO.findAll();
    }

    public List<Order> getOrdersByEmployee(int employeeId) throws SQLException {
        return orderDAO.findByEmployee(employeeId);
    }

    public List<Order> searchOrders(Integer customerId, Integer employeeId,
                                    Integer orderId, String status,
                                    LocalDateTime from, LocalDateTime to) throws SQLException {
        return orderDAO.search(customerId, employeeId, orderId, status, from, to);
    }

    public Order getOrderById(int orderId) throws SQLException {
        return orderDAO.findById(orderId);
    }

    public List<OrderLine> getLinesForOrder(int orderId) throws SQLException {
        return lineDAO.findByOrderId(orderId);
    }

    public Billing getBillingByOrder(int orderId) throws SQLException {
        return billingDAO.findByOrderId(orderId);
    }

    public List<Billing> getAllBillings() throws SQLException {
        return billingDAO.findAll();
    }

    public List<Promotion> getActivePromotions() throws SQLException {
        return promoDAO.findActive();
    }

    // ─────────────────────────────────────────────────────────────────────
    //  PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────

    private Order requireOrder(int orderId) throws Exception {
        Order o = orderDAO.findById(orderId);
        if (o == null) throw new IllegalArgumentException("Order #" + orderId + " not found.");
        return o;
    }

    private void requireDraft(Order order) {
        if (!"DRAFT".equals(order.getStatus()))
            throw new IllegalStateException(
                    "Order lines can only be modified when order is in DRAFT status.");
    }

    private void validateTransition(String current, String next) {
        // Allowed forward transitions
        boolean ok = switch (current) {
            case "DRAFT"      -> next.equals("CONFIRMED") || next.equals("CANCELLED");
            case "CONFIRMED"  -> next.equals("PAID")      || next.equals("CANCELLED");
            case "PAID"       -> next.equals("DELIVERING");
            case "DELIVERING" -> next.equals("COMPLETED") || next.equals("RETURNED");
            default           -> false;
        };
        if (!ok)
            throw new IllegalStateException(
                    "Invalid status transition: " + current + " → " + next);
    }

    private boolean wasStockDeducted(String status) {
        return switch (status) {
            case "CONFIRMED", "PAID", "DELIVERING" -> true;
            default -> false;
        };
    }

    private BigDecimal calcDiscount(Promotion promo, BigDecimal subTotal) {
        if (promo == null) return BigDecimal.ZERO;
        if (subTotal.compareTo(promo.getMinOrderValue()) < 0) return BigDecimal.ZERO;
        if ("PERCENT".equals(promo.getDiscountType())) {
            return subTotal.multiply(promo.getDiscountValue())
                           .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        // FIXED
        return promo.getDiscountValue().min(subTotal);
    }

    private void checkStock(List<OrderLine> lines) throws Exception {
        for (OrderLine line : lines) {
            Stock stock = stockDAO.getStockByProduct(line.getProductId());   // matches StockDAO API
            int available = stock != null ? stock.getQuantity() : 0;
            if (available < line.getQuantity())
                throw new IllegalStateException(
                        "Insufficient stock for product #" + line.getProductId()
                        + " (available: " + available + ", required: " + line.getQuantity() + ").");
        }
    }


    private void deductStock(Connection conn, List<OrderLine> lines,
                             int orderId, int actorId) throws SQLException {
        for (OrderLine line : lines) {
            // Delegate to StockDAO.applyChange – handles missing Stock row gracefully
            stockDAO.applyChange(conn, line.getProductId(), -line.getQuantity());
            insertStockLogTx(conn, line.getProductId(), -line.getQuantity(),
                    "OUT", orderId, actorId, "Order #" + orderId + " confirmed");
        }
    }

    private void restoreStock(Connection conn, List<OrderLine> lines,
                              int orderId, int actorId, String logType) throws SQLException {
        for (OrderLine line : lines) {
            stockDAO.applyChange(conn, line.getProductId(), line.getQuantity());
            insertStockLogTx(conn, line.getProductId(), line.getQuantity(),
                    logType, orderId, actorId, "Order #" + orderId + " " + logType.toLowerCase());
        }
    }

    private void recalcTotals(Order order) throws Exception {
        List<OrderLine> lines = lineDAO.findByOrderId(order.getOrderId());
        BigDecimal sub = lines.stream()
                .map(l -> l.getUnitPrice().multiply(BigDecimal.valueOf(l.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal disc = BigDecimal.ZERO;
        if (order.getPromoId() != null) {
            Promotion promo = promoDAO.findById(order.getPromoId());
            disc = calcDiscount(promo, sub);
        }
        orderDAO.updateTotals(order.getOrderId(), sub, disc, sub.subtract(disc).max(BigDecimal.ZERO));
    }

    // ── transaction-level SQL helpers ────────────────────────────────────

    private int insertOrderTx(Connection conn, Order order) throws SQLException {
        String sql = "INSERT INTO [Order] "
                + "(CustomerId,EmployeeId,AddressId,PromoId,OrderDate,Status,SubTotal,Discount,FinalTotal,Note) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, order.getCustomerId());
            ps.setInt(2, order.getEmployeeId());
            ps.setInt(3, order.getAddressId());
            if (order.getPromoId() != null) ps.setInt(4, order.getPromoId());
            else                            ps.setNull(4, Types.INTEGER);
            ps.setTimestamp(5, Timestamp.valueOf(order.getOrderDate()));
            ps.setString(6, "DRAFT");
            ps.setBigDecimal(7, order.getSubTotal());
            ps.setBigDecimal(8, order.getDiscount());
            ps.setBigDecimal(9, order.getFinalTotal());
            ps.setString(10, order.getNote());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Could not get generated OrderId.");
    }

    private void insertLineTx(Connection conn, OrderLine line) throws SQLException {
        String sql = "INSERT INTO OrderLine (OrderId,ProductId,Quantity,UnitPrice) VALUES (?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, line.getOrderId());
            ps.setInt(2, line.getProductId());
            ps.setInt(3, line.getQuantity());
            ps.setBigDecimal(4, line.getUnitPrice());
            ps.executeUpdate();
        }
    }

    private void incrementPromoTx(Connection conn, int promoId) throws SQLException {
        String sql = "UPDATE Promotion SET UsedCount=UsedCount+1 WHERE PromoId=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, promoId);
            ps.executeUpdate();
        }
    }

    /** Decrement UsedCount khi order bị CANCELLED hoặc RETURNED sau khi đã CONFIRMED. */
    private void decrementPromoTx(Connection conn, int promoId) throws SQLException {
        // Đảm bảo không xuống dưới 0
        String sql = "UPDATE Promotion SET UsedCount = CASE WHEN UsedCount > 0 THEN UsedCount-1 ELSE 0 END WHERE PromoId=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, promoId);
            ps.executeUpdate();
        }
    }

    private void updateStatusTx(Connection conn, int orderId, String status) throws SQLException {
        String sql = "UPDATE [Order] SET Status=? WHERE OrderId=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, orderId);
            ps.executeUpdate();
        }
    }

    private void insertStockLogTx(Connection conn, int productId, int changeQty,
                                  String logType, int refId, int actorId, String note) throws SQLException {
        String sql = "INSERT INTO StockLog (ProductId,ChangeQty,LogType,RefId,Note,ActorId,CreatedAt) "
                + "VALUES (?,?,?,?,?,?,GETDATE())";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setInt(2, changeQty);
            ps.setString(3, logType);
            ps.setInt(4, refId);
            ps.setString(5, note);
            ps.setInt(6, actorId);
            ps.executeUpdate();
        }
    }
}