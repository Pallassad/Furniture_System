package furniture_system.dao;

import furniture_system.config.DatabaseConfig;
import furniture_system.model.Account;
import furniture_system.model.Account.Role;
import furniture_system.model.Account.Status;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AccountDAO {

    public static final int MAX_FAILED_ATTEMPTS = 5;

    private static final String COLS =
        "AccountId, Username, PasswordHash, Role, Status, " +
        "Email, LastLoginAt, FailedAttempts, ResetToken, ResetTokenExpiry";

    // TIM KIEM
    public Account findByUsername(String username) {
        return one("SELECT " + COLS + " FROM Account WHERE Username = ?",
            ps -> ps.setString(1, username));
    }
    public Account findById(int id) {
        return one("SELECT " + COLS + " FROM Account WHERE AccountId = ?",
            ps -> ps.setInt(1, id));
    }
    public Account findByEmail(String email) {
        return one("SELECT " + COLS + " FROM Account WHERE Email = ?",
            ps -> ps.setString(1, email));
    }
    public Account findByValidResetToken(String token) {
        return one("SELECT " + COLS + " FROM Account WHERE ResetToken = ? AND ResetTokenExpiry > ?",
            ps -> { ps.setString(1, token);
                    ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now())); });
    }
    public List<Account> findAll() {
        List<Account> list = new ArrayList<>();
        String sql = "SELECT " + COLS + " FROM Account ORDER BY AccountId";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) { throw new RuntimeException("findAll failed", e); }
        return list;
    }

    // KIEM TRA
    public boolean isUsernameTaken(String username, int excludeId) {
        return count("Username = ? AND AccountId <> ?",
            ps -> { ps.setString(1, username); ps.setInt(2, excludeId); }) > 0;
    }
    public boolean isEmailTaken(String email, int excludeId) {
        return count("Email = ? AND AccountId <> ?",
            ps -> { ps.setString(1, email); ps.setInt(2, excludeId); }) > 0;
    }
    public boolean isLinkedToEmployee(int accountId) {
        String sql = "SELECT COUNT(*) FROM Employee WHERE AccountId = ?";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() && rs.getInt(1) > 0; }
        } catch (SQLException e) { throw new RuntimeException("isLinkedToEmployee failed", e); }
    }

    // DANG NHAP
    public void recordSuccessfulLogin(int accountId) {
        run("UPDATE Account SET LastLoginAt = ?, FailedAttempts = 0 WHERE AccountId = ?",
            ps -> { ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now())); ps.setInt(2, accountId); });
    }
    public void recordFailedLogin(int accountId) {
        String inc = "UPDATE Account SET FailedAttempts = FailedAttempts + 1 WHERE AccountId = ?";
        String sus = "UPDATE Account SET Status = 'SUSPENDED' " +
                     "WHERE AccountId = ? AND Role = 'EMPLOYEE' AND FailedAttempts >= ?";
        try (Connection c = DatabaseConfig.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement p1 = c.prepareStatement(inc)) {
                p1.setInt(1, accountId); p1.executeUpdate();
            }
            try (PreparedStatement p2 = c.prepareStatement(sus)) {
                p2.setInt(1, accountId); p2.setInt(2, MAX_FAILED_ATTEMPTS); p2.executeUpdate();
            }
            c.commit();
        } catch (SQLException e) { throw new RuntimeException("recordFailedLogin failed", e); }
    }

    // THEM / XOA
    public int insert(String username, String hash, Role role, Status status, String email) {
        String sql = "INSERT INTO Account (Username, PasswordHash, Role, Status, Email) VALUES (?,?,?,?,?)";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username); ps.setString(2, hash);
            ps.setString(3, role.name()); ps.setString(4, status.name());
            if (email == null || email.isBlank()) ps.setNull(5, Types.NVARCHAR);
            else ps.setString(5, email.trim());
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) { return k.next() ? k.getInt(1) : -1; }
        } catch (SQLException e) { throw new RuntimeException("insert failed", e); }
    }
    public boolean delete(int accountId) {
        return run("DELETE FROM Account WHERE AccountId = ?",
            ps -> ps.setInt(1, accountId)) == 1;
    }

    // CAP NHAT
    public boolean updateUsername(int id, String v)   { return upd("Username = ?", id, ps -> ps.setString(1, v)); }
    public boolean updatePassword(int id, String v)   { return upd("PasswordHash = ?", id, ps -> ps.setString(1, v)); }
    public boolean updateStatus(int id, Status v)     { return upd("Status = ?", id, ps -> ps.setString(1, v.name())); }
    public boolean updateRole(int id, Role v)         { return upd("Role = ?", id, ps -> ps.setString(1, v.name())); }
    public boolean resetFailedAttempts(int id)        { return upd("FailedAttempts = 0, Status = 'ACTIVE'", id, ps -> {}); }

    public boolean updateEmail(int accountId, String newEmail) {
        String sql = "UPDATE Account SET Email = ? WHERE AccountId = ?";
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (newEmail == null || newEmail.isBlank()) ps.setNull(1, Types.NVARCHAR);
            else ps.setString(1, newEmail.trim());
            ps.setInt(2, accountId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) { throw new RuntimeException("updateEmail failed", e); }
    }

    // RESET TOKEN
    public boolean saveResetToken(int accountId, String token, LocalDateTime expiry) {
        return run("UPDATE Account SET ResetToken = ?, ResetTokenExpiry = ? WHERE AccountId = ?",
            ps -> { ps.setString(1, token);
                    ps.setTimestamp(2, Timestamp.valueOf(expiry));
                    ps.setInt(3, accountId); }) == 1;
    }
    public boolean clearResetToken(int accountId) {
        return run("UPDATE Account SET ResetToken = NULL, ResetTokenExpiry = NULL WHERE AccountId = ?",
            ps -> ps.setInt(1, accountId)) == 1;
    }

    // HELPERS
    @FunctionalInterface interface S { void set(PreparedStatement ps) throws SQLException; }

    private Account one(String sql, S s) {
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            s.set(ps);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return map(rs); }
        } catch (SQLException e) { throw new RuntimeException("query failed", e); }
        return null;
    }
    private int run(String sql, S s) {
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            s.set(ps); return ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException("run failed", e); }
    }
    private int count(String where, S s) {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM Account WHERE " + where)) {
            s.set(ps);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        } catch (SQLException e) { throw new RuntimeException("count failed", e); }
    }
    private boolean upd(String set, int id, S s) {
        return run("UPDATE Account SET " + set + " WHERE AccountId = ?",
            ps -> { s.set(ps); ps.setInt(set.contains("?") ? 2 : 1, id); }) == 1;
    }
    private Account map(ResultSet rs) throws SQLException {
        Account a = new Account();
        a.setAccountId(rs.getInt("AccountId"));
        a.setUsername(rs.getString("Username"));
        a.setPasswordHash(rs.getString("PasswordHash"));
        a.setRole(Role.valueOf(rs.getString("Role")));
        a.setStatus(Status.valueOf(rs.getString("Status")));
        a.setEmail(rs.getString("Email"));
        Timestamp ll = rs.getTimestamp("LastLoginAt");
        if (ll != null) a.setLastLoginAt(ll.toLocalDateTime());
        a.setFailedAttempts(rs.getInt("FailedAttempts"));
        a.setResetToken(rs.getString("ResetToken"));
        Timestamp ex = rs.getTimestamp("ResetTokenExpiry");
        if (ex != null) a.setResetTokenExpiry(ex.toLocalDateTime());
        return a;
    }
}