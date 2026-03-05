package furniture_system.dao;

import furniture_system.model.Stock;
import furniture_system.model.StockLog;
import furniture_system.model.StockLog.LogType;
import furniture_system.config.DatabaseConfig;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * StockDAO – SQL Server (T-SQL) compatible.
 *
 * Schema notes:
 *   Stock    (ProductId PK, Quantity, ReorderLevel, LastUpdated)
 *   StockLog (LogId IDENTITY, ProductId, ChangeQty, LogType, RefId NULL,
 *             Note, ActorId FK->Employee.EmployeeId, CreatedAt)
 *
 * Key differences from MySQL version:
 *   - Column is "CreatedAt" not "LoggedAt"
 *   - ActorId references Employee.EmployeeId (not Account)
 *   - Employee.FullName (single column, not FirstName+LastName)
 *   - Use GETDATE() not NOW()
 *   - No AUTO_INCREMENT – use SCOPE_IDENTITY()
 */
public class StockDAO {

    /** 3.9.1 / 4.8.1 – All stock levels joined with product name */
    public List<Stock> getAllStock() throws SQLException {
        List<Stock> list = new ArrayList<>();
        String sql =
            "SELECT s.ProductId, s.Quantity, s.ReorderLevel, s.LastUpdated, " +
            "       p.Name AS ProductName " +
            "FROM Stock s " +
            "JOIN Product p ON p.ProductId = s.ProductId " +
            "ORDER BY p.Name";
        try (Connection con = DatabaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapStock(rs));
        }
        return list;
    }

    /** 4.8.2 – Single product stock */
    public Stock getStockByProduct(int productId) throws SQLException {
        String sql =
            "SELECT s.ProductId, s.Quantity, s.ReorderLevel, s.LastUpdated, " +
            "       p.Name AS ProductName " +
            "FROM Stock s " +
            "JOIN Product p ON p.ProductId = s.ProductId " +
            "WHERE s.ProductId = ?";
        try (Connection con = DatabaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapStock(rs);
            }
        }
        return null;
    }

    /**
     * Apply a stock change inside an existing connection/transaction.
     * Auto-creates a Stock row if the product has none yet.
     */
    public void applyChange(Connection con, int productId, int delta) throws SQLException {
        String upd = "UPDATE Stock SET Quantity = Quantity + ?, LastUpdated = GETDATE() " +
                     "WHERE ProductId = ?";
        int rows;
        try (PreparedStatement ps = con.prepareStatement(upd)) {
            ps.setInt(1, delta);
            ps.setInt(2, productId);
            rows = ps.executeUpdate();
        }
        if (rows == 0) {
            // Product exists but no Stock row yet – create one
            String ins = "INSERT INTO Stock (ProductId, Quantity, ReorderLevel) VALUES (?, ?, 5)";
            try (PreparedStatement pi = con.prepareStatement(ins)) {
                pi.setInt(1, productId);
                pi.setInt(2, Math.max(0, delta));
                pi.executeUpdate();
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  STOCK LOG
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 3.9.2 / 3.9.3 – Atomically insert a StockLog row AND update Stock.
     * Uses a transaction; rolls back on any error.
     *
     * @return generated LogId, or -1 on failure
     */
    public int logMovement(StockLog log) throws SQLException {
        try (Connection con = DatabaseConfig.getConnection()) {
            con.setAutoCommit(false);
            try {
                // 1. Insert log – SQL Server returns identity via SCOPE_IDENTITY()
                String insertLog =
                    "INSERT INTO StockLog (ProductId, ChangeQty, LogType, Note, ActorId) " +
                    "VALUES (?, ?, ?, ?, ?)";
                int logId = -1;
                try (PreparedStatement ps = con.prepareStatement(
                        insertLog, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, log.getProductId());
                    ps.setInt(2, log.getChangeQty());
                    ps.setString(3, log.getLogType().name());
                    if (log.getNote() == null || log.getNote().isBlank())
                         ps.setNull(4, Types.NVARCHAR);
                    else ps.setString(4, log.getNote());
                    ps.setInt(5, log.getActorId());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) logId = keys.getInt(1);
                    }
                }

                // 2. Update Stock
                applyChange(con, log.getProductId(), log.getChangeQty());

                con.commit();
                return logId;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    /**
     * 3.9.4 – View movement log with optional filters.
     * All params are nullable/optional (pass null to skip).
     *
     * NOTE: Employee.FullName is a single column in this schema.
     */
    public List<StockLog> getLogs(Integer productId, String logType,
                                  Timestamp from, Timestamp to) throws SQLException {
        List<StockLog> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT sl.LogId, sl.ProductId, sl.ChangeQty, sl.LogType, sl.Note, " +
            "       sl.ActorId, sl.CreatedAt, " +
            "       p.Name   AS ProductName, " +
            "       e.FullName AS ActorName " +
            "FROM StockLog sl " +
            "JOIN Product  p ON p.ProductId   = sl.ProductId " +
            "LEFT JOIN Employee e ON e.EmployeeId = sl.ActorId " +
            "WHERE 1=1 ");

        List<Object> params = new ArrayList<>();
        if (productId != null && productId > 0) {
            sql.append("AND sl.ProductId = ? "); params.add(productId);
        }
        if (logType != null && !logType.isBlank()) {
            sql.append("AND sl.LogType = ? "); params.add(logType);
        }
        if (from != null) { sql.append("AND sl.CreatedAt >= ? "); params.add(from); }
        if (to   != null) { sql.append("AND sl.CreatedAt <= ? "); params.add(to);   }
        sql.append("ORDER BY sl.CreatedAt DESC");

        try (Connection con = DatabaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if      (p instanceof Integer)   ps.setInt(i + 1, (Integer) p);
                else if (p instanceof String)    ps.setString(i + 1, (String) p);
                else if (p instanceof Timestamp) ps.setTimestamp(i + 1, (Timestamp) p);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapLog(rs));
            }
        }
        return list;
    }

    /**
     * 3.9.5 – Delete a StockLog entry and reverse its effect on Stock.
     * Wrapped in a transaction – fully atomic.
     */
    public boolean deleteLog(int logId) throws SQLException {
        try (Connection con = DatabaseConfig.getConnection()) {
            con.setAutoCommit(false);
            try {
                StockLog log = getLogById(con, logId);
                if (log == null) return false;

                // Reverse the stock delta
                applyChange(con, log.getProductId(), -log.getChangeQty());

                // Delete the log entry
                try (PreparedStatement ps = con.prepareStatement(
                        "DELETE FROM StockLog WHERE LogId = ?")) {
                    ps.setInt(1, logId);
                    ps.executeUpdate();
                }
                con.commit();
                return true;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    /** Fetch a single log entry within an existing connection (for delete reversal) */
    private StockLog getLogById(Connection con, int logId) throws SQLException {
        String sql =
            "SELECT LogId, ProductId, ChangeQty, LogType, Note, ActorId, CreatedAt " +
            "FROM StockLog WHERE LogId = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, logId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    StockLog log = new StockLog();
                    log.setLogId(rs.getInt("LogId"));
                    log.setProductId(rs.getInt("ProductId"));
                    log.setChangeQty(rs.getInt("ChangeQty"));
                    log.setLogType(LogType.valueOf(rs.getString("LogType")));
                    log.setNote(rs.getString("Note"));
                    log.setActorId(rs.getInt("ActorId"));
                    return log;
                }
            }
        }
        return null;
    }

    private Stock mapStock(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("LastUpdated");
        Stock s = new Stock(
            rs.getInt("ProductId"),
            rs.getInt("Quantity"),
            rs.getInt("ReorderLevel"),
            ts != null ? ts.toLocalDateTime() : null
        );
        s.setProductName(rs.getString("ProductName"));
        return s;
    }

    private StockLog mapLog(ResultSet rs) throws SQLException {
        StockLog log = new StockLog();
        log.setLogId(rs.getInt("LogId"));
        log.setProductId(rs.getInt("ProductId"));
        log.setChangeQty(rs.getInt("ChangeQty"));
        log.setLogType(LogType.valueOf(rs.getString("LogType")));
        log.setNote(rs.getString("Note"));
        log.setActorId(rs.getInt("ActorId"));
        // Column is CreatedAt in this schema (not LoggedAt)
        Timestamp ts = rs.getTimestamp("CreatedAt");
        if (ts != null) log.setLoggedAt(ts.toLocalDateTime());
        log.setProductName(rs.getString("ProductName"));
        log.setActorName(rs.getString("ActorName"));
        return log;
    }
}
