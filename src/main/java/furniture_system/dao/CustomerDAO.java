package furniture_system.dao;

import furniture_system.config.DatabaseConfig;
import furniture_system.model.Customer;
import furniture_system.model.Customer.Gender;
import furniture_system.model.Customer.Status;
import furniture_system.model.OrderSummary;
import furniture_system.model.OrderSummary.OrderLineItem;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * CustomerDAO – all database operations for Customer, plus purchase-history queries.
 */
public class CustomerDAO {

    private static final String SELECT_BASE =
        "SELECT CustomerId, FullName, Phone, Email, Gender, Status, CreatedAt FROM Customer ";

    // ─────────────────────────────────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────────────────────────────────

    public List<Customer> findAll() {
        return query(SELECT_BASE + "ORDER BY CustomerId", ps -> {});
    }

    /** 3.10.5 / 4.9.1 – Search by name, phone, email, status, or ID. */
    public List<Customer> search(String keyword) {
        if (keyword == null || keyword.isBlank()) return findAll();
        String like = "%" + keyword.trim() + "%";
        String sql  = SELECT_BASE +
            "WHERE FullName LIKE ? OR Phone LIKE ? OR Email LIKE ? " +
            "   OR Status LIKE ? OR CAST(CustomerId AS NVARCHAR) LIKE ? " +
            "ORDER BY CustomerId";
        return query(sql, ps -> {
            ps.setString(1, like); ps.setString(2, like);
            ps.setString(3, like); ps.setString(4, like);
            ps.setString(5, like);
        });
    }

    public Customer findById(int id) {
        List<Customer> list = query(
            SELECT_BASE + "WHERE CustomerId = ?",
            ps -> ps.setInt(1, id));
        return list.isEmpty() ? null : list.get(0);
    }

    public boolean isPhoneDuplicate(String phone, int excludeId) {
        String sql = "SELECT COUNT(*) FROM Customer WHERE Phone=? AND CustomerId<>?";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, phone); ps.setInt(2, excludeId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) { throw new RuntimeException("isPhoneDuplicate failed", e); }
    }

    public boolean isEmailDuplicate(String email, int excludeId) {
        if (email == null || email.isBlank()) return false;
        String sql = "SELECT COUNT(*) FROM Customer WHERE Email=? AND CustomerId<>?";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email); ps.setInt(2, excludeId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) { throw new RuntimeException("isEmailDuplicate failed", e); }
    }

    /** Rule: cannot hard-delete if customer has orders. */
    public boolean hasOrders(int customerId) {
        String sql = "SELECT COUNT(*) FROM [Order] WHERE CustomerId=?";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) { throw new RuntimeException("hasOrders failed", e); }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CREATE  3.10.2 / 4.9.2
    // ─────────────────────────────────────────────────────────────────────────
    public int insert(Customer c) {
        String sql = "INSERT INTO Customer (FullName, Phone, Email, Gender, Status) VALUES (?,?,?,?,?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindWrite(ps, c);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            return keys.next() ? keys.getInt(1) : -1;
        } catch (SQLException e) { throw new RuntimeException("insert customer failed", e); }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UPDATE  3.10.3
    // ─────────────────────────────────────────────────────────────────────────
    public boolean update(Customer c) {
        String sql = "UPDATE Customer SET FullName=?,Phone=?,Email=?,Gender=?,Status=? WHERE CustomerId=?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindWrite(ps, c);
            ps.setInt(6, c.getCustomerId());
            return ps.executeUpdate() == 1;
        } catch (SQLException e) { throw new RuntimeException("update customer failed", e); }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STATUS  3.10.4
    // ─────────────────────────────────────────────────────────────────────────
    public boolean updateStatus(int customerId, Status newStatus) {
        String sql = "UPDATE Customer SET Status=? WHERE CustomerId=?";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, newStatus.name()); ps.setInt(2, customerId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) { throw new RuntimeException("updateStatus failed", e); }
    }

    public boolean delete(int customerId) {
        String sql = "DELETE FROM Customer WHERE CustomerId=?";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) { throw new RuntimeException("delete customer failed", e); }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PURCHASE HISTORY  4.9.3
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns all orders for a customer, each with Billing status.
     * OrderLine details are loaded separately via loadLines().
     */
    public List<OrderSummary> getPurchaseHistory(int customerId) {
        String sql =
            "SELECT o.OrderId, o.CustomerId, o.OrderDate, o.Status, " +
            "       o.SubTotal, o.Discount, o.FinalTotal, o.Note, " +
            "       b.BillingStatus, b.PaymentMethod " +
            "FROM [Order] o " +
            "LEFT JOIN Billing b ON b.OrderId = o.OrderId " +
            "WHERE o.CustomerId = ? " +
            "ORDER BY o.OrderDate DESC";

        List<OrderSummary> list = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                OrderSummary s = new OrderSummary();
                s.setOrderId(rs.getInt("OrderId"));
                s.setCustomerId(rs.getInt("CustomerId"));
                Timestamp ts = rs.getTimestamp("OrderDate");
                if (ts != null) s.setOrderDate(ts.toLocalDateTime());
                s.setStatus(rs.getString("Status"));
                s.setSubTotal(rs.getBigDecimal("SubTotal"));
                s.setDiscount(rs.getBigDecimal("Discount"));
                s.setFinalTotal(rs.getBigDecimal("FinalTotal"));
                s.setNote(rs.getString("Note"));
                s.setBillingStatus(rs.getString("BillingStatus"));
                s.setPaymentMethod(rs.getString("PaymentMethod"));
                s.setLines(loadLines(c, s.getOrderId()));
                list.add(s);
            }
        } catch (SQLException e) { throw new RuntimeException("getPurchaseHistory failed", e); }
        return list;
    }

    private List<OrderLineItem> loadLines(Connection c, int orderId) throws SQLException {
        String sql =
            "SELECT ol.LineId, p.Name AS ProductName, ol.Quantity, ol.UnitPrice, ol.LineTotal " +
            "FROM OrderLine ol " +
            "JOIN Product p ON p.ProductId = ol.ProductId " +
            "WHERE ol.OrderId = ? ORDER BY ol.LineId";
        List<OrderLineItem> lines = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                OrderLineItem li = new OrderLineItem();
                li.setLineId(rs.getInt("LineId"));
                li.setProductName(rs.getString("ProductName"));
                li.setQuantity(rs.getInt("Quantity"));
                li.setUnitPrice(rs.getBigDecimal("UnitPrice"));
                li.setLineTotal(rs.getBigDecimal("LineTotal"));
                lines.add(li);
            }
        }
        return lines;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STATISTICS
    // ─────────────────────────────────────────────────────────────────────────

    public List<Object[]> countByStatus() {
        String sql = "SELECT Status, COUNT(*) FROM Customer GROUP BY Status";
        List<Object[]> rows = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) rows.add(new Object[]{ rs.getString(1), rs.getInt(2) });
        } catch (SQLException e) { throw new RuntimeException("countByStatus failed", e); }
        return rows;
    }

    public List<Object[]> countByGender() {
        String sql = "SELECT ISNULL(Gender,'UNKNOWN'), COUNT(*) FROM Customer GROUP BY Gender";
        List<Object[]> rows = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) rows.add(new Object[]{ rs.getString(1), rs.getInt(2) });
        } catch (SQLException e) { throw new RuntimeException("countByGender failed", e); }
        return rows;
    }

    /** New customers by month (last 12 months). */
    public List<Object[]> newByMonth() {
        String sql =
            "SELECT FORMAT(CreatedAt,'yyyy-MM') AS Mon, COUNT(*) AS cnt " +
            "FROM Customer " +
            "WHERE CreatedAt >= DATEADD(MONTH,-11,DATEFROMPARTS(YEAR(GETDATE()),MONTH(GETDATE()),1)) " +
            "GROUP BY FORMAT(CreatedAt,'yyyy-MM') ORDER BY Mon";
        List<Object[]> rows = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) rows.add(new Object[]{ rs.getString("Mon"), rs.getInt("cnt") });
        } catch (SQLException e) { throw new RuntimeException("newByMonth failed", e); }
        return rows;
    }

    /** Top 5 customers by total spending. */
    public List<Object[]> topSpenders() {
        String sql =
            "SELECT TOP 5 c.FullName, SUM(o.FinalTotal) AS Total " +
            "FROM [Order] o JOIN Customer c ON c.CustomerId = o.CustomerId " +
            "WHERE o.Status NOT IN ('CANCELLED','RETURNED') " +
            "GROUP BY c.CustomerId, c.FullName ORDER BY Total DESC";
        List<Object[]> rows = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) rows.add(new Object[]{ rs.getString(1), rs.getBigDecimal(2) });
        } catch (SQLException e) { throw new RuntimeException("topSpenders failed", e); }
        return rows;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────
    @FunctionalInterface
    private interface Binder { void bind(PreparedStatement ps) throws SQLException; }

    private List<Customer> query(String sql, Binder b) {
        List<Customer> list = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            b.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) { throw new RuntimeException("query failed", e); }
        return list;
    }

    private void bindWrite(PreparedStatement ps, Customer c) throws SQLException {
        ps.setString(1, c.getFullName());
        ps.setString(2, c.getPhone());
        ps.setString(3, blank2null(c.getEmail()));
        ps.setString(4, c.getGender() != null ? c.getGender().name() : null);
        ps.setString(5, c.getStatus() != null ? c.getStatus().name() : Status.ACTIVE.name());
    }

    private Customer mapRow(ResultSet rs) throws SQLException {
        Customer c = new Customer();
        c.setCustomerId(rs.getInt("CustomerId"));
        c.setFullName(rs.getString("FullName"));
        c.setPhone(rs.getString("Phone"));
        c.setEmail(rs.getString("Email"));
        String g = rs.getString("Gender");
        if (g != null) c.setGender(Gender.valueOf(g));
        c.setStatus(Status.valueOf(rs.getString("Status")));
        Timestamp ts = rs.getTimestamp("CreatedAt");
        if (ts != null) c.setCreatedAt(ts.toLocalDateTime());
        return c;
    }

    private String blank2null(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
