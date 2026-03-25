package furniture_system.dao;

import furniture_system.config.DatabaseConfig;
import furniture_system.model.Billing;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class BillingDAO {

    // ─────────────────────────────────────────────────────────────────────
    //  CREATE
    // ─────────────────────────────────────────────────────────────────────

    public int insert(Billing billing) throws SQLException {
        String sql = "INSERT INTO Billing (OrderId, IssueDate, PaymentMethod, PaidAmount, BillingStatus, Note) "
                + "VALUES (?,?,?,?,?,?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, billing.getOrderId());
            ps.setTimestamp(2, Timestamp.valueOf(
                    billing.getIssueDate() != null ? billing.getIssueDate() : LocalDateTime.now()));
            ps.setString(3, billing.getPaymentMethod());
            ps.setBigDecimal(4, billing.getPaidAmount());
            ps.setString(5, billing.getBillingStatus());
            ps.setString(6, billing.getNote());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Failed to obtain generated InvoiceId.");
    }

    // ─────────────────────────────────────────────────────────────────────
    //  READ
    // ─────────────────────────────────────────────────────────────────────

    public Billing findByOrderId(int orderId) throws SQLException {
        String sql = "SELECT b.*, c.FullName AS CustomerName, o.FinalTotal "
                + "FROM Billing b "
                + "JOIN [Order] o ON o.OrderId = b.OrderId "
                + "JOIN Customer c ON c.CustomerId = o.CustomerId "
                + "WHERE b.OrderId = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public Billing findById(int invoiceId) throws SQLException {
        String sql = "SELECT b.*, c.FullName AS CustomerName, o.FinalTotal "
                + "FROM Billing b "
                + "JOIN [Order] o ON o.OrderId = b.OrderId "
                + "JOIN Customer c ON c.CustomerId = o.CustomerId "
                + "WHERE b.InvoiceId = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public List<Billing> findAll() throws SQLException {
        String sql = "SELECT b.*, c.FullName AS CustomerName, o.FinalTotal "
                + "FROM Billing b "
                + "JOIN [Order] o ON o.OrderId = b.OrderId "
                + "JOIN Customer c ON c.CustomerId = o.CustomerId "
                + "ORDER BY b.IssueDate DESC";
        try (Connection conn = DatabaseConfig.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return mapList(rs);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  DELETE
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Xoá cứng một Billing record.
     * Chỉ gọi khi BillingStatus = UNPAID hoặc VOID (kiểm tra ở Service).
     */
    public boolean delete(int invoiceId) throws SQLException {
        String sql = "DELETE FROM Billing WHERE InvoiceId = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, invoiceId);
            return ps.executeUpdate() > 0;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  UPDATE
    // ─────────────────────────────────────────────────────────────────────

    public boolean update(Billing billing) throws SQLException {
        String sql = "UPDATE Billing SET PaymentMethod=?, PaidAmount=?, BillingStatus=?, Note=? "
                + "WHERE InvoiceId=?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, billing.getPaymentMethod());
            ps.setBigDecimal(2, billing.getPaidAmount());
            ps.setString(3, billing.getBillingStatus());
            ps.setString(4, billing.getNote());
            ps.setInt(5, billing.getInvoiceId());
            return ps.executeUpdate() > 0;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────────────────

    private List<Billing> mapList(ResultSet rs) throws SQLException {
        List<Billing> list = new ArrayList<>();
        while (rs.next()) list.add(mapRow(rs));
        return list;
    }

    private Billing mapRow(ResultSet rs) throws SQLException {
        Billing b = new Billing();
        b.setInvoiceId(rs.getInt("InvoiceId"));
        b.setOrderId(rs.getInt("OrderId"));
        Timestamp ts = rs.getTimestamp("IssueDate");
        if (ts != null) b.setIssueDate(ts.toLocalDateTime());
        b.setPaymentMethod(rs.getString("PaymentMethod"));
        b.setPaidAmount(rs.getBigDecimal("PaidAmount"));
        b.setBillingStatus(rs.getString("BillingStatus"));
        b.setNote(rs.getString("Note"));
        try { b.setCustomerName(rs.getString("CustomerName")); } catch (SQLException ignored) {}
        try { b.setFinalTotal(rs.getBigDecimal("FinalTotal")); } catch (SQLException ignored) {}
        return b;
    }
}
