package furniture_system.dao;

import furniture_system.config.DatabaseConfig;
import furniture_system.model.DeliveryAddress;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DeliveryAddressDAO
 * Handles all DB operations for DeliveryAddress table.
 * Business rules enforced here:
 *  - Only 1 IsDefault=true per Customer  → unsetOtherDefaults() called in same transaction
 *  - Soft-delete only (Status = INACTIVE) if address is linked to an Order
 *  - Phone: 9-11 numeric digits (enforced at Service layer, DB has CHECK constraint)
 */
public class DeliveryAddressDAO {

    // ─────────────────────────────────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────────────────────────────────

    /** All addresses (Admin: all statuses, ordered default-first). */
    public List<DeliveryAddress> findAll() throws SQLException {
        String sql = """
                SELECT da.*, c.FullName AS CustomerName
                FROM DeliveryAddress da
                JOIN Customer c ON c.CustomerId = da.CustomerId
                ORDER BY da.IsDefault DESC, da.CreatedAt DESC
                """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return mapList(rs);
        }
    }

    /** Active addresses for a specific customer (Employee: order creation). */
    public List<DeliveryAddress> findByCustomerId(int customerId) throws SQLException {
        String sql = """
                SELECT da.*, c.FullName AS CustomerName
                FROM DeliveryAddress da
                JOIN Customer c ON c.CustomerId = da.CustomerId
                WHERE da.CustomerId = ? AND da.Status = 'ACTIVE'
                ORDER BY da.IsDefault DESC, da.CreatedAt DESC
                """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            return mapList(ps.executeQuery());
        }
    }

    /** Find by PK (any status). */
    public DeliveryAddress findById(int addressId) throws SQLException {
        String sql = """
                SELECT da.*, c.FullName AS CustomerName
                FROM DeliveryAddress da
                JOIN Customer c ON c.CustomerId = da.CustomerId
                WHERE da.AddressId = ?
                """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, addressId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    /**
     * Admin search: by customer name, city, district, receiver name, phone, or status.
     * Keyword is case-insensitive partial match.
     */
    public List<DeliveryAddress> search(String keyword) throws SQLException {
        String like = "%" + keyword.trim() + "%";
        String sql = """
                SELECT da.*, c.FullName AS CustomerName
                FROM DeliveryAddress da
                JOIN Customer c ON c.CustomerId = da.CustomerId
                WHERE   c.FullName       LIKE ?
                   OR   da.ReceiverName  LIKE ?
                   OR   da.Phone         LIKE ?
                   OR   da.City          LIKE ?
                   OR   da.District      LIKE ?
                   OR   da.Ward          LIKE ?
                   OR   da.Status        LIKE ?
                ORDER BY da.IsDefault DESC, da.CreatedAt DESC
                """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 1; i <= 7; i++) ps.setString(i, like);
            return mapList(ps.executeQuery());
        }
    }

    /** Check if address is linked to any Order (used before hard-delete guard). */
    public boolean isLinkedToOrder(int addressId) throws SQLException {
        String sql = "SELECT COUNT(1) FROM [Order] WHERE AddressId = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, addressId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WRITE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Insert new address.
     * If isDefault=true, unsets all other defaults for the same customer
     * atomically in a single transaction.
     *
     * @return generated AddressId
     */
    public int insert(DeliveryAddress addr) throws SQLException {
        String sql = """
                INSERT INTO DeliveryAddress
                    (CustomerId, ReceiverName, Phone, AddressLine, Ward, District, City,
                     IsDefault, Status, CreatedAt)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """;
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (addr.isDefault()) {
                    unsetOtherDefaults(conn, addr.getCustomerId(), -1);
                }
                int newId;
                try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, addr.getCustomerId());
                    ps.setString(2, addr.getReceiverName());
                    ps.setString(3, addr.getPhone());
                    ps.setString(4, addr.getAddressLine());
                    ps.setString(5, addr.getWard());
                    ps.setString(6, addr.getDistrict());
                    ps.setString(7, addr.getCity());
                    ps.setBoolean(8, addr.isDefault());
                    ps.setString(9, "ACTIVE");
                    ps.setTimestamp(10, Timestamp.valueOf(LocalDateTime.now()));
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) newId = rs.getInt(1);
                        else throw new SQLException("Failed to obtain generated AddressId.");
                    }
                }
                conn.commit();
                return newId;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Update existing address.
     * If isDefault=true, unsets other defaults for same customer in same transaction.
     */
    public boolean update(DeliveryAddress addr) throws SQLException {
        String sql = """
                UPDATE DeliveryAddress
                SET CustomerId   = ?,
                    ReceiverName = ?,
                    Phone        = ?,
                    AddressLine  = ?,
                    Ward         = ?,
                    District     = ?,
                    City         = ?,
                    IsDefault    = ?,
                    Status       = ?
                WHERE AddressId  = ?
                """;
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (addr.isDefault()) {
                    unsetOtherDefaults(conn, addr.getCustomerId(), addr.getAddressId());
                }
                int rows;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, addr.getCustomerId());
                    ps.setString(2, addr.getReceiverName());
                    ps.setString(3, addr.getPhone());
                    ps.setString(4, addr.getAddressLine());
                    ps.setString(5, addr.getWard());
                    ps.setString(6, addr.getDistrict());
                    ps.setString(7, addr.getCity());
                    ps.setBoolean(8, addr.isDefault());
                    ps.setString(9, addr.getStatus());
                    ps.setInt(10, addr.getAddressId());
                    rows = ps.executeUpdate();
                }
                conn.commit();
                return rows > 0;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Soft-delete: set Status = INACTIVE.
     * Must always be used instead of hard-delete when address is linked to an Order.
     */
    public boolean softDelete(int addressId) throws SQLException {
        String sql = "UPDATE DeliveryAddress SET Status = 'INACTIVE' WHERE AddressId = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, addressId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Hard-delete: permanently remove address.
    /**
     * Hard-delete: permanently remove an INACTIVE address.
     * Only call this when the address has no Orders referencing it.
     * Since orders only use ACTIVE addresses, an INACTIVE address
     * should never be referenced by any new order.
     */
    public boolean hardDelete(int addressId) throws SQLException {
        String sql = "DELETE FROM DeliveryAddress WHERE AddressId = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, addressId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Set a specific address as default for a customer.
     * Unsets all other defaults atomically.
     */
    public boolean setDefault(int addressId, int customerId) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                unsetOtherDefaults(conn, customerId, addressId);
                String sql = "UPDATE DeliveryAddress SET IsDefault = 1 WHERE AddressId = ?";
                int rows;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, addressId);
                    rows = ps.executeUpdate();
                }
                conn.commit();
                return rows > 0;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STATISTICS (Admin reports)
    // ─────────────────────────────────────────────────────────────────────────

    /** Number of orders grouped by City. */
    public List<Object[]> countOrdersByCity() throws SQLException {
        String sql = """
                SELECT da.City, COUNT(o.OrderId) AS OrderCount
                FROM [Order] o
                JOIN DeliveryAddress da ON da.AddressId = o.AddressId
                GROUP BY da.City
                ORDER BY OrderCount DESC
                """;
        return runStatsQuery(sql);
    }

    /** Number of orders grouped by District. */
    public List<Object[]> countOrdersByDistrict() throws SQLException {
        String sql = """
                SELECT da.District, COUNT(o.OrderId) AS OrderCount
                FROM [Order] o
                JOIN DeliveryAddress da ON da.AddressId = o.AddressId
                GROUP BY da.District
                ORDER BY OrderCount DESC
                """;
        return runStatsQuery(sql);
    }

    /** Rate of orders that used a default address vs non-default. */
    public List<Object[]> defaultAddressUsageRate() throws SQLException {
        String sql = """
                SELECT
                    CASE da.IsDefault WHEN 1 THEN N'Default' ELSE N'Non-Default' END AS AddressType,
                    COUNT(o.OrderId) AS OrderCount
                FROM [Order] o
                JOIN DeliveryAddress da ON da.AddressId = o.AddressId
                GROUP BY da.IsDefault
                """;
        return runStatsQuery(sql);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Unsets IsDefault for all addresses of a customer EXCEPT excludeAddressId.
     * Must be called within an open transaction.
     */
    private void unsetOtherDefaults(Connection conn, int customerId, int excludeAddressId)
            throws SQLException {
        String sql = """
                UPDATE DeliveryAddress
                SET IsDefault = 0
                WHERE CustomerId = ? AND AddressId <> ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ps.setInt(2, excludeAddressId);
            ps.executeUpdate();
        }
    }

    private List<Object[]> runStatsQuery(String sql) throws SQLException {
        List<Object[]> result = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            while (rs.next()) {
                Object[] row = new Object[cols];
                for (int i = 0; i < cols; i++) row[i] = rs.getObject(i + 1);
                result.add(row);
            }
        }
        return result;
    }

    private List<DeliveryAddress> mapList(ResultSet rs) throws SQLException {
        List<DeliveryAddress> list = new ArrayList<>();
        while (rs.next()) list.add(mapRow(rs));
        return list;
    }

    private DeliveryAddress mapRow(ResultSet rs) throws SQLException {
        DeliveryAddress a = new DeliveryAddress();
        a.setAddressId(rs.getInt("AddressId"));
        a.setCustomerId(rs.getInt("CustomerId"));
        a.setReceiverName(rs.getString("ReceiverName"));
        a.setPhone(rs.getString("Phone"));
        a.setAddressLine(rs.getString("AddressLine"));
        a.setWard(rs.getString("Ward"));
        a.setDistrict(rs.getString("District"));
        a.setCity(rs.getString("City"));
        a.setDefault(rs.getBoolean("IsDefault"));
        a.setStatus(rs.getString("Status"));
        Timestamp ts = rs.getTimestamp("CreatedAt");
        if (ts != null) a.setCreatedAt(ts.toLocalDateTime());
        // optional join column
        try { a.setCustomerName(rs.getString("CustomerName")); } catch (SQLException ignored) {}
        return a;
    }
}