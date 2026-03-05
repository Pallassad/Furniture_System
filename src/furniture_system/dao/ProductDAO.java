package furniture_system.dao;

import furniture_system.config.DatabaseConfig;
import furniture_system.model.Product;
import furniture_system.model.Supplier;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the Product table (SQL Server).
 *
 * Key SQL Server differences handled here:
 *  - NVARCHAR parameters  → setNString() for proper Unicode support
 *  - IDENTITY columns     → no need to set ProductId on INSERT
 *  - [Order] reserved word pattern not needed here, but kept style consistent
 *  - DATETIME2 columns    → getTimestamp() still works via JDBC
 *  - T-SQL TOP / OFFSET   → standard JDBC PreparedStatement
 */
public class ProductDAO {

    // ── Base SELECT with JOINs (shared by all queries) ─────────────────────
    private static final String SELECT_BASE = """
            SELECT
                p.ProductId,
                p.Name,
                p.TypeId,
                p.SupplierId,
                p.Price,
                p.Description,
                p.WarrantyMonths,
                p.Status,
                p.CreatedAt,
                ft.TypeName,
                s.Name AS SupplierName
            FROM Product p
            LEFT JOIN FurnitureType ft ON p.TypeId     = ft.TypeId
            LEFT JOIN Supplier      s  ON p.SupplierId = s.SupplierId
            """;

    // ══════════════════════════════════════════════════════════════════════
    // READ – Admin (all statuses)
    // ══════════════════════════════════════════════════════════════════════
    public List<Product> getAll() throws SQLException {
        String sql = SELECT_BASE + " ORDER BY p.ProductId DESC";
        try (Connection c  = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs         = ps.executeQuery()) {
            return mapRows(rs);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // READ – Employee (ACTIVE only)
    // ══════════════════════════════════════════════════════════════════════
    public List<Product> getActive() throws SQLException {
        String sql = SELECT_BASE
                   + " WHERE p.Status = N'ACTIVE' ORDER BY p.ProductId DESC";
        try (Connection c  = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs         = ps.executeQuery()) {
            return mapRows(rs);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // SEARCH – Admin (all statuses)
    // Searches: Name, TypeName, SupplierName, Status, ProductId
    // ══════════════════════════════════════════════════════════════════════
    public List<Product> search(String keyword) throws SQLException {
        String sql = SELECT_BASE + """
                WHERE  p.Name        LIKE ?
                    OR ft.TypeName   LIKE ?
                    OR s.Name        LIKE ?
                    OR p.Status      LIKE ?
                    OR CAST(p.ProductId AS NVARCHAR) LIKE ?
                ORDER BY p.ProductId DESC
                """;
        String like = "%" + keyword + "%";
        try (Connection c  = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            // Use setNString for NVARCHAR LIKE comparisons
            for (int i = 1; i <= 5; i++) ps.setNString(i, like);
            try (ResultSet rs = ps.executeQuery()) {
                return mapRows(rs);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // SEARCH – Employee (ACTIVE only, with optional price range)
    // ══════════════════════════════════════════════════════════════════════
    public List<Product> searchActive(String keyword,
                                       BigDecimal minPrice,
                                       BigDecimal maxPrice) throws SQLException {
        StringBuilder sql    = new StringBuilder(SELECT_BASE)
                .append(" WHERE p.Status = N'ACTIVE' ");
        List<Object>  params = new ArrayList<>();
        List<Boolean> isNStr = new ArrayList<>(); // track which params are NVARCHAR

        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (p.Name LIKE ? OR ft.TypeName LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            params.add(like); isNStr.add(true);
            params.add(like); isNStr.add(true);
        }
        if (minPrice != null) {
            sql.append(" AND p.Price >= ? ");
            params.add(minPrice); isNStr.add(false);
        }
        if (maxPrice != null) {
            sql.append(" AND p.Price <= ? ");
            params.add(maxPrice); isNStr.add(false);
        }
        sql.append(" ORDER BY p.ProductId DESC");

        try (Connection c  = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                if (isNStr.get(i)) {
                    ps.setNString(i + 1, (String) params.get(i));
                } else {
                    ps.setObject(i + 1, params.get(i));
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                return mapRows(rs);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // INSERT
    // Uses IDENTITY(1,1) – ProductId is auto-generated, not set.
    // ══════════════════════════════════════════════════════════════════════
    public boolean insert(Product p) throws SQLException {
        String sql = """
                INSERT INTO Product
                    (Name, TypeId, SupplierId, Price, Description, WarrantyMonths, Status)
                VALUES
                    (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection c  = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setNString(1,     p.getName());
            ps.setInt(2,         p.getTypeId());
            ps.setInt(3,         p.getSupplierId());
            ps.setBigDecimal(4,  p.getPrice());
            // Description is NVARCHAR(MAX) nullable
            if (p.getDescription() != null) {
                ps.setNString(5, p.getDescription());
            } else {
                ps.setNull(5, Types.NVARCHAR);
            }
            ps.setInt(6,         p.getWarrantyMonths());
            ps.setNString(7,     p.getStatus().name());   // stores "ACTIVE" / "INACTIVE" / "OUT_OF_STOCK"
            return ps.executeUpdate() > 0;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // UPDATE
    // ══════════════════════════════════════════════════════════════════════
    public boolean update(Product p) throws SQLException {
        String sql = """
                UPDATE Product
                SET Name           = ?,
                    TypeId         = ?,
                    SupplierId     = ?,
                    Price          = ?,
                    Description    = ?,
                    WarrantyMonths = ?,
                    Status         = ?
                WHERE ProductId = ?
                """;
        try (Connection c  = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setNString(1,    p.getName());
            ps.setInt(2,        p.getTypeId());
            ps.setInt(3,        p.getSupplierId());
            ps.setBigDecimal(4, p.getPrice());
            if (p.getDescription() != null) {
                ps.setNString(5, p.getDescription());
            } else {
                ps.setNull(5, Types.NVARCHAR);
            }
            ps.setInt(6,        p.getWarrantyMonths());
            ps.setNString(7,    p.getStatus().name());
            ps.setInt(8,        p.getProductId());
            return ps.executeUpdate() > 0;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // SOFT-DELETE  →  Status = 'INACTIVE'
    // ══════════════════════════════════════════════════════════════════════
    public boolean deactivate(int productId) throws SQLException {
        String sql = "UPDATE Product SET Status = N'INACTIVE' WHERE ProductId = ?";
        try (Connection c  = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, productId);
            return ps.executeUpdate() > 0;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // SUPPLIERS LIST  – for ComboBox (only ACTIVE suppliers)
    // ══════════════════════════════════════════════════════════════════════
    public List<Supplier> getActiveSuppliers() throws SQLException {
        String sql = "SELECT SupplierId, Name FROM Supplier " +
                     "WHERE Status = N'ACTIVE' ORDER BY Name";
        List<Supplier> list = new ArrayList<>();
        try (Connection c  = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs         = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Supplier(
                        rs.getInt("SupplierId"),
                        rs.getNString("Name")));
            }
        }
        return list;
    }

    // ══════════════════════════════════════════════════════════════════════
    // ROW MAPPER
    // DATETIME2 → getTimestamp() works correctly via SQL Server JDBC driver
    // ══════════════════════════════════════════════════════════════════════
    private List<Product> mapRows(ResultSet rs) throws SQLException {
        List<Product> list = new ArrayList<>();
        while (rs.next()) {
            Product p = new Product();
            p.setProductId(rs.getInt("ProductId"));
            p.setName(rs.getNString("Name"));
            p.setTypeId(rs.getInt("TypeId"));
            p.setSupplierId(rs.getInt("SupplierId"));
            p.setPrice(rs.getBigDecimal("Price"));
            p.setDescription(rs.getNString("Description"));    // nullable NVARCHAR(MAX)
            p.setWarrantyMonths(rs.getInt("WarrantyMonths"));
            p.setStatus(Product.Status.fromDb(rs.getString("Status")));
            p.setTypeName(rs.getNString("TypeName"));
            p.setSupplierName(rs.getNString("SupplierName"));

            Timestamp ts = rs.getTimestamp("CreatedAt");
            if (ts != null) p.setCreatedAt(ts.toLocalDateTime());

            list.add(p);
        }
        return list;
    }
}
