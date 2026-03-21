package furniture_system.dao;

import furniture_system.config.DatabaseConfig;
import furniture_system.model.OrderLine;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class OrderLineDAO {

    // ─────────────────────────────────────────────────────────────────────
    //  CREATE
    // ─────────────────────────────────────────────────────────────────────

    public int insert(OrderLine line) throws SQLException {
        String sql = "INSERT INTO OrderLine (OrderId, ProductId, Quantity, UnitPrice) "
                + "VALUES (?,?,?,?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, line.getOrderId());
            ps.setInt(2, line.getProductId());
            ps.setInt(3, line.getQuantity());
            ps.setBigDecimal(4, line.getUnitPrice());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Failed to obtain generated LineId.");
    }

    // ─────────────────────────────────────────────────────────────────────
    //  READ
    // ─────────────────────────────────────────────────────────────────────

    public List<OrderLine> findByOrderId(int orderId) throws SQLException {
        String sql = "SELECT ol.*, p.Name AS ProductName "
                + "FROM OrderLine ol "
                + "JOIN Product p ON p.ProductId = ol.ProductId "
                + "WHERE ol.OrderId = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            return mapList(ps.executeQuery());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  UPDATE  (only allowed while Order is DRAFT – enforced by DB trigger)
    // ─────────────────────────────────────────────────────────────────────

    public boolean update(OrderLine line) throws SQLException {
        String sql = "UPDATE OrderLine SET Quantity=?, UnitPrice=? WHERE LineId=?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, line.getQuantity());
            ps.setBigDecimal(2, line.getUnitPrice());
            ps.setInt(3, line.getLineId());
            return ps.executeUpdate() > 0;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  DELETE
    // ─────────────────────────────────────────────────────────────────────

    public boolean delete(int lineId) throws SQLException {
        String sql = "DELETE FROM OrderLine WHERE LineId=?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, lineId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteByOrderId(int orderId) throws SQLException {
        String sql = "DELETE FROM OrderLine WHERE OrderId=?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            return ps.executeUpdate() >= 0;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────────────────

    private List<OrderLine> mapList(ResultSet rs) throws SQLException {
        List<OrderLine> list = new ArrayList<>();
        while (rs.next()) {
            OrderLine ol = new OrderLine();
            ol.setLineId(rs.getInt("LineId"));
            ol.setOrderId(rs.getInt("OrderId"));
            ol.setProductId(rs.getInt("ProductId"));
            ol.setQuantity(rs.getInt("Quantity"));
            ol.setUnitPrice(rs.getBigDecimal("UnitPrice"));
            ol.setLineTotal(rs.getBigDecimal("LineTotal"));
            try { ol.setProductName(rs.getString("ProductName")); } catch (SQLException ignored) {}
            list.add(ol);
        }
        return list;
    }
}
