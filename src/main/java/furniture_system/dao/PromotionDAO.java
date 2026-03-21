package furniture_system.dao;

import furniture_system.config.DatabaseConfig;
import furniture_system.model.Promotion;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PromotionDAO {

    public List<Promotion> findActive() throws SQLException {
        String sql = "SELECT * FROM Promotion "
                + "WHERE Status='ACTIVE' "
                + "  AND StartDate <= GETDATE() AND EndDate >= GETDATE() "
                + "  AND (UsageLimit IS NULL OR UsedCount < UsageLimit) "
                + "ORDER BY Code";
        try (Connection conn = DatabaseConfig.getConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return mapList(rs);
        }
    }

    public List<Promotion> findAll() throws SQLException {
        String sql = "SELECT * FROM Promotion ORDER BY CreatedAt DESC";
        try (Connection conn = DatabaseConfig.getConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return mapList(rs);
        }
    }

    public Promotion findById(int promoId) throws SQLException {
        String sql = "SELECT * FROM Promotion WHERE PromoId=?";
        try (Connection conn = DatabaseConfig.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, promoId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public Promotion findByCode(String code) throws SQLException {
        String sql = "SELECT * FROM Promotion WHERE Code=?";
        try (Connection conn = DatabaseConfig.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    /**
     * Increment UsedCount by 1 after applying to an order.
     */
    public void incrementUsed(int promoId) throws SQLException {
        String sql = "UPDATE Promotion SET UsedCount = UsedCount + 1 WHERE PromoId=?";
        try (Connection conn = DatabaseConfig.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, promoId);
            ps.executeUpdate();
        }
    }

    // ================= ADMIN MANAGEMENT =================
    /**
     * Admin – Add new promotion
     */
    public void insert(Promotion p) throws SQLException {
        String sql = """
            INSERT INTO Promotion
              (Code,Name,DiscountType,DiscountValue,StartDate,EndDate,
               MinOrderValue,UsageLimit,UsedCount,Status)
            VALUES (?,?,?,?,?,?,?,?,0,?)
            """;
        try (Connection conn = DatabaseConfig.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p.getCode());
            ps.setString(2, p.getName());
            ps.setString(3, p.getDiscountType());
            ps.setBigDecimal(4, p.getDiscountValue());
            ps.setTimestamp(5, Timestamp.valueOf(p.getStartDate()));
            ps.setTimestamp(6, Timestamp.valueOf(p.getEndDate()));
            ps.setBigDecimal(7, p.getMinOrderValue());

            if (p.getUsageLimit() == null) {
                ps.setNull(8, Types.INTEGER);
            } else {
                ps.setInt(8, p.getUsageLimit());
            }

            ps.setString(9, p.getStatus());
            ps.executeUpdate();
        }
    }

    /**
     * Admin – Update promotion
     */
    public void update(Promotion p) throws SQLException {
        String sql = """
            UPDATE Promotion SET
              Code=?, Name=?, DiscountType=?, DiscountValue=?,
              StartDate=?, EndDate=?, MinOrderValue=?,
              UsageLimit=?, Status=?
            WHERE PromoId=?
            """;
        try (Connection conn = DatabaseConfig.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p.getCode());
            ps.setString(2, p.getName());
            ps.setString(3, p.getDiscountType());
            ps.setBigDecimal(4, p.getDiscountValue());
            ps.setTimestamp(5, Timestamp.valueOf(p.getStartDate()));
            ps.setTimestamp(6, Timestamp.valueOf(p.getEndDate()));
            ps.setBigDecimal(7, p.getMinOrderValue());

            if (p.getUsageLimit() == null) {
                ps.setNull(8, Types.INTEGER);
            } else {
                ps.setInt(8, p.getUsageLimit());
            }

            ps.setString(9, p.getStatus());
            ps.setInt(10, p.getPromoId());

            ps.executeUpdate();
        }
    }

    /**
     * Admin – Disable promotion (soft delete)
     */
    public void disable(int promoId) throws SQLException {
        String sql = "UPDATE Promotion SET Status='DISABLED' WHERE PromoId=?";
        try (Connection conn = DatabaseConfig.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, promoId);
            ps.executeUpdate();
        }
    }

    // ================= MAPPING =================
    private List<Promotion> mapList(ResultSet rs) throws SQLException {
        List<Promotion> list = new ArrayList<>();
        while (rs.next()) {
            list.add(mapRow(rs));
        }
        return list;
    }

    private Promotion mapRow(ResultSet rs) throws SQLException {
        Promotion p = new Promotion();
        p.setPromoId(rs.getInt("PromoId"));
        p.setCode(rs.getString("Code"));
        p.setName(rs.getString("Name"));
        p.setDiscountType(rs.getString("DiscountType"));
        p.setDiscountValue(rs.getBigDecimal("DiscountValue"));

        Timestamp s = rs.getTimestamp("StartDate");
        if (s != null) {
            p.setStartDate(s.toLocalDateTime());
        }

        Timestamp e = rs.getTimestamp("EndDate");
        if (e != null) {
            p.setEndDate(e.toLocalDateTime());
        }

        p.setMinOrderValue(rs.getBigDecimal("MinOrderValue"));

        int limit = rs.getInt("UsageLimit");
        p.setUsageLimit(rs.wasNull() ? null : limit);

        p.setUsedCount(rs.getInt("UsedCount"));
        p.setStatus(rs.getString("Status"));

        Timestamp c = rs.getTimestamp("CreatedAt");
        if (c != null) {
            p.setCreatedAt(c.toLocalDateTime());
        }

        return p;
    }
}
