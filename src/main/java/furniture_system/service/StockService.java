package furniture_system.service;

import furniture_system.config.DatabaseConfig;
import furniture_system.dao.StockDAO;
import furniture_system.model.Stock;
import furniture_system.model.StockLog;
import furniture_system.model.StockLog.LogType;

import java.sql.*;
import java.util.List;

/**
 * StockService - business logic for Inventory / Stock Management.
 *
 * IMPORTANT - Actor resolution:
 *   StockLog.ActorId is FK -> Employee.EmployeeId (NOT Account.AccountId).
 *   Controllers must resolve the EmployeeId linked to the logged-in Account
 *   and pass it here. The Admin seed account has NO Employee row by default,
 *   so admin controllers should look up or store the EmployeeId separately.
 */
public class StockService {

    private final StockDAO dao = new StockDAO();

    // 3.9.1 / 4.8.1
    public List<Stock> getAllStock() {
        try { return dao.getAllStock(); }
        catch (SQLException e) { throw new RuntimeException("DB error: " + e.getMessage(), e); }
    }

    // 4.8.2
    public Stock getStock(int productId) {
        if (productId <= 0) throw new IllegalArgumentException("Invalid product ID.");
        try { return dao.getStockByProduct(productId); }
        catch (SQLException e) { throw new RuntimeException("DB error: " + e.getMessage(), e); }
    }

    /**
     * 3.9.2 - Stock In.
     * @param actorId Employee.EmployeeId of the actor (must be > 0 per FK constraint)
     */
    public void stockIn(int productId, int changeQty, String note, int actorId) {
        if (productId <= 0) throw new IllegalArgumentException("Invalid product.");
        if (changeQty <= 0) throw new IllegalArgumentException("Stock In quantity must be positive.");
        validateNote(note, false);
        validateActor(actorId);
        try { dao.logMovement(new StockLog(productId, changeQty, LogType.IN, note, actorId)); }
        catch (SQLException e) { throw new RuntimeException("DB error: " + e.getMessage(), e); }
    }

    /**
     * 3.9.3 - Adjust Stock (positive or negative, never 0).
     * Note is required for audit trail.
     * @param actorId Employee.EmployeeId
     */
    public void adjustStock(int productId, int changeQty, String note, int actorId) {
        if (productId <= 0) throw new IllegalArgumentException("Invalid product.");
        if (changeQty == 0) throw new IllegalArgumentException("Adjustment quantity must not be 0.");
        if (note == null || note.isBlank())
            throw new IllegalArgumentException("A reason/note is required for stock adjustment.");
        validateNote(note, true);
        validateActor(actorId);
        try { dao.logMovement(new StockLog(productId, changeQty, LogType.ADJUST, note, actorId)); }
        catch (SQLException e) { throw new RuntimeException("DB error: " + e.getMessage(), e); }
    }

    /** 3.9.4 - View log with optional filters (pass null to skip). */
    public List<StockLog> getMovementLog(Integer productId, String logType,
                                         Timestamp from, Timestamp to) {
        try { return dao.getLogs(productId, logType, from, to); }
        catch (SQLException e) { throw new RuntimeException("DB error: " + e.getMessage(), e); }
    }

    /**
     * 3.9.5 - Delete log and reverse stock. Only IN and ADJUST allowed.
     * Caller must show confirmation dialog before invoking.
     */
    public void deleteLog(int logId, LogType logType) {
        if (logId <= 0) throw new IllegalArgumentException("Invalid log ID.");
        if (logType != LogType.IN && logType != LogType.ADJUST)
            throw new IllegalArgumentException("Only IN and ADJUST log entries can be deleted.");
        try {
            if (!dao.deleteLog(logId))
                throw new RuntimeException("Delete failed - log entry not found.");
        } catch (SQLException e) {
            throw new RuntimeException("DB error: " + e.getMessage(), e);
        }
    }

    /**
     * Get current Reorder Level for a product.
     * Returns 0 if no stock row found.
     */
    public int getReorderLevel(int productId) throws SQLException {
        if (productId <= 0) throw new IllegalArgumentException("Invalid product ID.");
        String sql = "SELECT ReorderLevel FROM Stock WHERE ProductId = ?";
        try (Connection con = DatabaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("ReorderLevel") : 0;
            }
        }
    }

    /**
     * Update Reorder Level for a product.
     * @param reorderLevel must be >= 0
     */
    public void updateReorderLevel(int productId, int reorderLevel) throws SQLException {
        if (productId <= 0) throw new IllegalArgumentException("Invalid product ID.");
        if (reorderLevel < 0) throw new IllegalArgumentException("Reorder level cannot be negative.");
        String sql = "UPDATE Stock SET ReorderLevel = ? WHERE ProductId = ?";
        try (Connection con = DatabaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, reorderLevel);
            ps.setInt(2, productId);
            ps.executeUpdate();
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private void validateNote(String note, boolean required) {
        if (required && (note == null || note.isBlank()))
            throw new IllegalArgumentException("Note / reason is required.");
        if (note != null && note.length() > 255)
            throw new IllegalArgumentException("Note must not exceed 255 characters.");
    }

    private void validateActor(int actorId) {
        if (actorId <= 0)
            throw new IllegalArgumentException(
                "A valid Employee ID is required to record stock changes.");
    }
}