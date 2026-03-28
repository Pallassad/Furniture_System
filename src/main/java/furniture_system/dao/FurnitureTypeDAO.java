package furniture_system.dao;

import furniture_system.config.DatabaseConfig;
import furniture_system.model.FurnitureType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for the FurnitureType table (SQL Server).
 * All queries use PreparedStatement to prevent SQL Injection.
 */
public class FurnitureTypeDAO {

    // ════════════════════════════════════════════════════════════════════════
    //  GET ALL
    // ════════════════════════════════════════════════════════════════════════
    public List<FurnitureType> getAll() throws SQLException {
        String sql = "SELECT TypeId, TypeName, Description, Status, CreatedAt "
                   + "FROM FurnitureType "
                   + "ORDER BY TypeId";

        List<FurnitureType> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  GET ACTIVE  (used for Employee ComboBox)
    // ════════════════════════════════════════════════════════════════════════
    public List<FurnitureType> getActive() throws SQLException {
        String sql = "SELECT TypeId, TypeName, Description, Status, CreatedAt "
                   + "FROM FurnitureType "
                   + "WHERE Status = 'ACTIVE' "
                   + "ORDER BY TypeName";

        List<FurnitureType> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  SEARCH  (by TypeId, TypeName, Status)
    // ════════════════════════════════════════════════════════════════════════
    public List<FurnitureType> search(String keyword) throws SQLException {
        String sql = "SELECT TypeId, TypeName, Description, Status, CreatedAt "
                   + "FROM FurnitureType "
                   + "WHERE LOWER(TypeName) LIKE ? "
                   + "   OR LOWER(Status)   LIKE ? "
                   + "   OR CAST(TypeId AS NVARCHAR) LIKE ? "
                   + "ORDER BY TypeId";

        String kw = "%" + keyword.toLowerCase() + "%";
        List<FurnitureType> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setNString(1, kw);
            ps.setNString(2, kw);
            ps.setString(3, kw);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  GET BY ID
    // ════════════════════════════════════════════════════════════════════════
    public FurnitureType getById(int typeId) throws SQLException {
        String sql = "SELECT TypeId, TypeName, Description, Status, CreatedAt "
                   + "FROM FurnitureType WHERE TypeId = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, typeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        }
        return null;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  INSERT
    // ════════════════════════════════════════════════════════════════════════
    public boolean insert(FurnitureType ft) throws SQLException {
        String sql = "INSERT INTO FurnitureType (TypeName, Description, Status) "
                   + "VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setNString(1, ft.getTypeName().trim());
            if (ft.getDescription() != null && !ft.getDescription().isEmpty())
                ps.setNString(2, ft.getDescription());
            else
                ps.setNull(2, Types.NVARCHAR);
            ps.setNString(3, ft.getStatus());
            return ps.executeUpdate() > 0;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  UPDATE
    // ════════════════════════════════════════════════════════════════════════
    public boolean update(FurnitureType ft) throws SQLException {
        String sql = "UPDATE FurnitureType "
                   + "SET TypeName = ?, Description = ?, Status = ? "
                   + "WHERE TypeId = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setNString(1, ft.getTypeName().trim());
            if (ft.getDescription() != null && !ft.getDescription().isEmpty())
                ps.setNString(2, ft.getDescription());
            else
                ps.setNull(2, Types.NVARCHAR);
            ps.setNString(3, ft.getStatus());
            ps.setInt(4, ft.getTypeId());
            return ps.executeUpdate() > 0;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  SOFT DELETE — set Status = 'INACTIVE'
    // ════════════════════════════════════════════════════════════════════════
    public boolean deactivate(int typeId) throws SQLException {
        String sql = "UPDATE FurnitureType SET Status = 'INACTIVE' WHERE TypeId = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, typeId);
            return ps.executeUpdate() > 0;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  HARD DELETE — permanently remove record
    // ════════════════════════════════════════════════════════════════════════
    public boolean delete(int typeId) throws SQLException {
        String sql = "DELETE FROM FurnitureType WHERE TypeId = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, typeId);
            return ps.executeUpdate() > 0;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  CHECK UNIQUE TypeName
    // ════════════════════════════════════════════════════════════════════════
    public boolean isTypeNameTaken(String typeName, int excludeId) throws SQLException {
        String sql = "SELECT COUNT(1) FROM FurnitureType "
                   + "WHERE LOWER(TypeName) = LOWER(?) AND TypeId <> ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setNString(1, typeName.trim());
            ps.setInt(2, excludeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  CHECK whether TypeId is referenced by ANY Product (including INACTIVE)
    // ════════════════════════════════════════════════════════════════════════
    public boolean hasLinkedProducts(int typeId) throws SQLException {
        String sql = "SELECT COUNT(1) FROM Product WHERE TypeId = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, typeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Checks only ACTIVE Products (used for soft-deactivate warning).
     */
    public boolean hasActiveLinkedProducts(int typeId) throws SQLException {
        String sql = "SELECT COUNT(1) FROM Product WHERE TypeId = ? AND Status <> 'INACTIVE'";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, typeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  HELPER: map ResultSet → FurnitureType
    // ════════════════════════════════════════════════════════════════════════
    private FurnitureType map(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("CreatedAt");
        return new FurnitureType(
            rs.getInt("TypeId"),
            rs.getString("TypeName"),
            rs.getString("Description"),
            rs.getString("Status"),
            ts != null ? ts.toLocalDateTime() : null
        );
    }
}