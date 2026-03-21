package furniture_system.dao;

import furniture_system.config.DatabaseConfig;
import furniture_system.model.Supplier;
import furniture_system.model.SupplierProduct;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * SupplierDAO – SQL Server (T-SQL) compatible.
 * Schema: Supplier(SupplierId, Name, Phone, Email, Address, Status, CreatedAt)
 *         SupplierProduct(SupplierId, ProductId, ImportPrice, LeadTimeDays, UpdatedAt)
 */
public class SupplierDAO {

   

    // ══════════════════════════════════════════════════════════════════════
    //  SUPPLIER CRUD
    // ══════════════════════════════════════════════════════════════════════

    /** 3.13.1 – All suppliers */
    public List<Supplier> getAll() throws SQLException {
        List<Supplier> list = new ArrayList<>();
        String sql = "SELECT SupplierId, Name, Phone, Email, Address, Status, CreatedAt " +
                     "FROM Supplier ORDER BY Name";
        try (Connection con = DatabaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    /** 3.13.5 – Search by keyword */
    public List<Supplier> search(String keyword) throws SQLException {
        List<Supplier> list = new ArrayList<>();
        String sql = "SELECT SupplierId, Name, Phone, Email, Address, Status, CreatedAt " +
                     "FROM Supplier " +
                     "WHERE Name LIKE ? OR Phone LIKE ? OR Email LIKE ? OR Status LIKE ? " +
                     "ORDER BY Name";
        String kw = "%" + keyword.trim() + "%";
        try (Connection con = DatabaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 1; i <= 4; i++) ps.setString(i, kw);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    /** 3.13.2 – Insert new supplier */
    public boolean insert(Supplier s) throws SQLException {
        String sql = "INSERT INTO Supplier (Name, Phone, Email, Address, Status) " +
                     "VALUES (?, ?, ?, ?, ?)";
        try (Connection con = DatabaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, s.getName());
            ps.setString(2, s.getPhone());
            setNullable(ps, 3, s.getEmail());
            setNullable(ps, 4, s.getAddress());
            ps.setString(5, s.getStatus());
            return ps.executeUpdate() > 0;
        }
    }

    /** 3.13.3 – Update supplier */
    public boolean update(Supplier s) throws SQLException {
        String sql = "UPDATE Supplier SET Name=?, Phone=?, Email=?, Address=?, Status=? " +
                     "WHERE SupplierId=?";
        try (Connection con = DatabaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, s.getName());
            ps.setString(2, s.getPhone());
            setNullable(ps, 3, s.getEmail());
            setNullable(ps, 4, s.getAddress());
            ps.setString(5, s.getStatus());
            ps.setInt(6, s.getSupplierId());
            return ps.executeUpdate() > 0;
        }
    }

    /** 3.13.4 – Soft-delete: set Status = INACTIVE */
    public boolean deactivate(int supplierId) throws SQLException {
        String sql = "UPDATE Supplier SET Status='INACTIVE' WHERE SupplierId=?";
        try (Connection con = DatabaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, supplierId);
            return ps.executeUpdate() > 0;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SUPPLIER-PRODUCT LINK
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 3.13.6 – Link supplier to product.
     * SQL Server does not support ON DUPLICATE KEY UPDATE;
     * use MERGE (upsert) instead.
     */
    public boolean linkProduct(SupplierProduct sp) throws SQLException {
        // MERGE upsert: insert if not exist, update if exists
        String sql =
            "MERGE SupplierProduct AS target " +
            "USING (SELECT ? AS SupplierId, ? AS ProductId) AS src " +
            "  ON target.SupplierId = src.SupplierId AND target.ProductId = src.ProductId " +
            "WHEN MATCHED THEN " +
            "  UPDATE SET ImportPrice=?, LeadTimeDays=?, UpdatedAt=GETDATE() " +
            "WHEN NOT MATCHED THEN " +
            "  INSERT (SupplierId, ProductId, ImportPrice, LeadTimeDays) " +
            "  VALUES (?, ?, ?, ?);";
        try (Connection con = DatabaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            // USING clause params
            ps.setInt(1, sp.getSupplierId());
            ps.setInt(2, sp.getProductId());
            // UPDATE SET params
            ps.setBigDecimal(3, sp.getImportPrice());
            ps.setInt(4, sp.getLeadTimeDays());
            // INSERT params
            ps.setInt(5, sp.getSupplierId());
            ps.setInt(6, sp.getProductId());
            ps.setBigDecimal(7, sp.getImportPrice());
            ps.setInt(8, sp.getLeadTimeDays());
            return ps.executeUpdate() > 0;
        }
    }

    /** Get all product links for a given supplier */
    public List<SupplierProduct> getLinkedProducts(int supplierId) throws SQLException {
        List<SupplierProduct> list = new ArrayList<>();
        String sql =
            "SELECT sp.SupplierId, sp.ProductId, sp.ImportPrice, sp.LeadTimeDays, " +
            "       p.Name AS ProductName, s.Name AS SupplierName " +
            "FROM SupplierProduct sp " +
            "JOIN Product  p ON p.ProductId  = sp.ProductId " +
            "JOIN Supplier s ON s.SupplierId = sp.SupplierId " +
            "WHERE sp.SupplierId = ? " +
            "ORDER BY p.Name";
        try (Connection con = DatabaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, supplierId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SupplierProduct sp = new SupplierProduct(
                        rs.getInt("SupplierId"),
                        rs.getInt("ProductId"),
                        rs.getBigDecimal("ImportPrice"),
                        rs.getInt("LeadTimeDays")
                    );
                    sp.setProductName(rs.getString("ProductName"));
                    sp.setSupplierName(rs.getString("SupplierName"));
                    list.add(sp);
                }
            }
        }
        return list;
    }

    /** Remove a supplier-product link */
    public boolean unlinkProduct(int supplierId, int productId) throws SQLException {
        String sql = "DELETE FROM SupplierProduct WHERE SupplierId=? AND ProductId=?";
        try (Connection con = DatabaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, supplierId);
            ps.setInt(2, productId);
            return ps.executeUpdate() > 0;
        }
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    // Sửa lại (đúng):
private Supplier map(ResultSet rs) throws SQLException {
    Supplier s = new Supplier();
    s.setSupplierId(rs.getInt("SupplierId"));
    s.setName(rs.getString("Name"));
    s.setPhone(rs.getString("Phone"));
    s.setEmail(rs.getString("Email"));
    s.setAddress(rs.getString("Address"));
    s.setStatus(rs.getString("Status"));  // ✅ String trực tiếp
    return s;
}

    /** Set a nullable String parameter (NULL if blank/null) */
    private void setNullable(PreparedStatement ps, int idx, String val) throws SQLException {
        if (val == null || val.isBlank()) ps.setNull(idx, Types.NVARCHAR);
        else ps.setString(idx, val);
    }
}
