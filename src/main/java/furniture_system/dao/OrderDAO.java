package furniture_system.dao;

import furniture_system.config.DatabaseConfig;
import furniture_system.model.Order;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class OrderDAO {

    // ─────────────────────────────────────────────────────────────────────
    //  CREATE
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Inserts a new Order (Status = DRAFT) and returns the generated OrderId.
     */
    public int createOrder(Order order) throws SQLException {
        String sql = "INSERT INTO [Order] "
                + "(CustomerId, EmployeeId, AddressId, PromoId, OrderDate, Status, "
                + " SubTotal, Discount, FinalTotal, Note) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?)";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, order.getCustomerId());
            ps.setInt(2, order.getEmployeeId());
            ps.setInt(3, order.getAddressId());
            if (order.getPromoId() != null) ps.setInt(4, order.getPromoId());
            else                            ps.setNull(4, Types.INTEGER);
            ps.setTimestamp(5, Timestamp.valueOf(
                    order.getOrderDate() != null ? order.getOrderDate() : LocalDateTime.now()));
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
        throw new SQLException("Failed to obtain generated OrderId.");
    }

    // ─────────────────────────────────────────────────────────────────────
    //  READ – single
    // ─────────────────────────────────────────────────────────────────────

    public Order findById(int orderId) throws SQLException {
        String sql = "SELECT o.*, c.FullName AS CustomerName, e.FullName AS EmployeeName "
                + "FROM [Order] o "
                + "JOIN Customer c ON c.CustomerId = o.CustomerId "
                + "JOIN Employee e ON e.EmployeeId = o.EmployeeId "
                + "WHERE o.OrderId = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────
    //  READ – list / search
    // ─────────────────────────────────────────────────────────────────────

    /** Returns all orders (for Admin view). */
    public List<Order> findAll() throws SQLException {
        String sql = "SELECT o.*, c.FullName AS CustomerName, e.FullName AS EmployeeName "
                + "FROM [Order] o "
                + "JOIN Customer c ON c.CustomerId = o.CustomerId "
                + "JOIN Employee e ON e.EmployeeId = o.EmployeeId "
                + "ORDER BY o.OrderDate DESC";
        return executeQuery(sql);
    }

    /** Returns all orders belonging to one employee (for Employee view). */
    public List<Order> findByEmployee(int employeeId) throws SQLException {
        String sql = "SELECT o.*, c.FullName AS CustomerName, e.FullName AS EmployeeName "
                + "FROM [Order] o "
                + "JOIN Customer c ON c.CustomerId = o.CustomerId "
                + "JOIN Employee e ON e.EmployeeId = o.EmployeeId "
                + "WHERE o.EmployeeId = ? "
                + "ORDER BY o.OrderDate DESC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, employeeId);
            return mapList(ps.executeQuery());
        }
    }

    /**
     * Flexible search used by Admin: any combination of filters.
     * Pass null / 0 to skip a filter.
     */
    public List<Order> search(Integer customerId, Integer employeeId,
                              Integer orderId, String status,
                              LocalDateTime fromDate, LocalDateTime toDate) throws SQLException {

        StringBuilder sb = new StringBuilder(
                "SELECT o.*, c.FullName AS CustomerName, e.FullName AS EmployeeName "
                        + "FROM [Order] o "
                        + "JOIN Customer c ON c.CustomerId = o.CustomerId "
                        + "JOIN Employee e ON e.EmployeeId = o.EmployeeId "
                        + "WHERE 1=1 ");

        List<Object> params = new ArrayList<>();

        if (customerId != null && customerId > 0) { sb.append("AND o.CustomerId=? ");  params.add(customerId); }
        if (employeeId != null && employeeId > 0) { sb.append("AND o.EmployeeId=? ");  params.add(employeeId); }
        if (orderId    != null && orderId    > 0) { sb.append("AND o.OrderId=? ");     params.add(orderId); }
        if (status     != null && !status.isBlank()) { sb.append("AND o.Status=? ");   params.add(status); }
        if (fromDate   != null) { sb.append("AND o.OrderDate>=? ");  params.add(Timestamp.valueOf(fromDate)); }
        if (toDate     != null) { sb.append("AND o.OrderDate<=? ");  params.add(Timestamp.valueOf(toDate)); }

        sb.append("ORDER BY o.OrderDate DESC");

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sb.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            return mapList(ps.executeQuery());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  UPDATE – status
    // ─────────────────────────────────────────────────────────────────────

    public boolean updateStatus(int orderId, String newStatus) throws SQLException {
        String sql = "UPDATE [Order] SET Status=? WHERE OrderId=?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setInt(2, orderId);
            return ps.executeUpdate() > 0;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  UPDATE – totals (called after OrderLines change)
    // ─────────────────────────────────────────────────────────────────────

    public boolean updateTotals(int orderId, java.math.BigDecimal subTotal,
                                java.math.BigDecimal discount,
                                java.math.BigDecimal finalTotal) throws SQLException {
        String sql = "UPDATE [Order] SET SubTotal=?, Discount=?, FinalTotal=? WHERE OrderId=?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, subTotal);
            ps.setBigDecimal(2, discount);
            ps.setBigDecimal(3, finalTotal);
            ps.setInt(4, orderId);
            return ps.executeUpdate() > 0;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────────────────

    private List<Order> executeQuery(String sql) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return mapList(rs);
        }
    }

    private List<Order> mapList(ResultSet rs) throws SQLException {
        List<Order> list = new ArrayList<>();
        while (rs.next()) list.add(mapRow(rs));
        return list;
    }

    private Order mapRow(ResultSet rs) throws SQLException {
        Order o = new Order();
        o.setOrderId(rs.getInt("OrderId"));
        o.setCustomerId(rs.getInt("CustomerId"));
        o.setEmployeeId(rs.getInt("EmployeeId"));
        o.setAddressId(rs.getInt("AddressId"));
        int promoId = rs.getInt("PromoId");
        o.setPromoId(rs.wasNull() ? null : promoId);
        Timestamp ts = rs.getTimestamp("OrderDate");
        if (ts != null) o.setOrderDate(ts.toLocalDateTime());
        o.setStatus(rs.getString("Status"));
        o.setSubTotal(rs.getBigDecimal("SubTotal"));
        o.setDiscount(rs.getBigDecimal("Discount"));
        o.setFinalTotal(rs.getBigDecimal("FinalTotal"));
        o.setNote(rs.getString("Note"));
        // JOIN columns
        try { o.setCustomerName(rs.getString("CustomerName")); } catch (SQLException ignored) {}
        try { o.setEmployeeName(rs.getString("EmployeeName")); } catch (SQLException ignored) {}
        return o;
    }
}
