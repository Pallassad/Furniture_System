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

    // ─────────────────────────────────────────────────────────────────────────
    // AUTHENTICATION
    // ─────────────────────────────────────────────────────────────────────────

    public Account findByUsername(String username) {
        String sql = "SELECT AccountId, Username, PasswordHash, Role, Status, " +
                     "CreatedAt, LastLoginAt, FailedAttempts " +
                     "FROM Account WHERE Username = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("findByUsername failed", e);
        }
        return null;
    }

    public void recordSuccessfulLogin(int accountId) {
        String sql = "UPDATE Account SET LastLoginAt = ?, FailedAttempts = 0 WHERE AccountId = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(2, accountId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("recordSuccessfulLogin failed", e);
        }
    }

    public void recordFailedLogin(int accountId) {
        String increment = "UPDATE Account SET FailedAttempts = FailedAttempts + 1 WHERE AccountId = ?";
        String suspend   = "UPDATE Account SET Status = 'SUSPENDED' " +
                           "WHERE AccountId = ? AND FailedAttempts >= ?";
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps1 = conn.prepareStatement(increment)) {
                ps1.setInt(1, accountId);
                ps1.executeUpdate();
            }
            try (PreparedStatement ps2 = conn.prepareStatement(suspend)) {
                ps2.setInt(1, accountId);
                ps2.setInt(2, MAX_FAILED_ATTEMPTS);
                ps2.executeUpdate();
            }
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("recordFailedLogin failed", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CRUD
    // ─────────────────────────────────────────────────────────────────────────

    public List<Account> findAll() {
        List<Account> list = new ArrayList<>();
        String sql = "SELECT AccountId, Username, PasswordHash, Role, Status, " +
                     "CreatedAt, LastLoginAt, FailedAttempts FROM Account ORDER BY AccountId";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("findAll failed", e);
        }
        return list;
    }

    public Account findById(int accountId) {
        String sql = "SELECT AccountId, Username, PasswordHash, Role, Status, " +
                     "CreatedAt, LastLoginAt, FailedAttempts FROM Account WHERE AccountId = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("findById failed", e);
        }
        return null;
    }

    public boolean isUsernameTaken(String username, int excludeId) {
        String sql = "SELECT COUNT(*) FROM Account WHERE Username = ? AND AccountId <> ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setInt(2, excludeId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            throw new RuntimeException("isUsernameTaken failed", e);
        }
    }

    /** Check if account is linked to an Employee record. */
    public boolean isLinkedToEmployee(int accountId) {
        String sql = "SELECT COUNT(*) FROM Employee WHERE AccountId = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            throw new RuntimeException("isLinkedToEmployee failed", e);
        }
    }

    // ── ADD ───────────────────────────────────────────────────────────────────
    /** Inserts a new Account. Returns generated AccountId. */
    public int insert(String username, String passwordHash, Role role, Status status) {
        String sql = "INSERT INTO Account (Username, PasswordHash, Role, Status) VALUES (?,?,?,?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, role.name());
            ps.setString(4, status.name());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            return keys.next() ? keys.getInt(1) : -1;
        } catch (SQLException e) {
            throw new RuntimeException("insert account failed", e);
        }
    }

    // ── DELETE ────────────────────────────────────────────────────────────────
    /** Hard-deletes an account. Only allowed when not linked to an Employee. */
    public boolean delete(int accountId) {
        String sql = "DELETE FROM Account WHERE AccountId = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("delete account failed", e);
        }
    }

    // ── EXISTING UPDATE METHODS (unchanged) ───────────────────────────────────

    /** Updates Username. Caller must verify uniqueness first. */
    public boolean updateUsername(int accountId, String newUsername) {
        String sql = "UPDATE Account SET Username = ? WHERE AccountId = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newUsername);
            ps.setInt(2, accountId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("updateUsername failed", e);
        }
    }

    /** Updates Password hash. */
    public boolean updatePassword(int accountId, String newHash) {
        String sql = "UPDATE Account SET PasswordHash = ? WHERE AccountId = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newHash);
            ps.setInt(2, accountId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("updatePassword failed", e);
        }
    }

    public boolean updateStatus(int accountId, Status newStatus) {
        String sql = "UPDATE Account SET Status = ? WHERE AccountId = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus.name());
            ps.setInt(2, accountId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("updateStatus failed", e);
        }
    }

    public boolean updateRole(int accountId, Role newRole) {
        String sql = "UPDATE Account SET Role = ? WHERE AccountId = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newRole.name());
            ps.setInt(2, accountId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("updateRole failed", e);
        }
    }

    public boolean resetFailedAttempts(int accountId) {
        String sql = "UPDATE Account SET FailedAttempts = 0, Status = 'ACTIVE' WHERE AccountId = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("resetFailedAttempts failed", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPER
    // ─────────────────────────────────────────────────────────────────────────

    private Account mapRow(ResultSet rs) throws SQLException {
        Account a = new Account();
        a.setAccountId(rs.getInt("AccountId"));
        a.setUsername(rs.getString("Username"));
        a.setPasswordHash(rs.getString("PasswordHash"));
        a.setRole(Role.valueOf(rs.getString("Role")));
        a.setStatus(Status.valueOf(rs.getString("Status")));
        Timestamp created = rs.getTimestamp("CreatedAt");
        if (created != null) a.setCreatedAt(created.toLocalDateTime());
        Timestamp lastLogin = rs.getTimestamp("LastLoginAt");
        if (lastLogin != null) a.setLastLoginAt(lastLogin.toLocalDateTime());
        a.setFailedAttempts(rs.getInt("FailedAttempts"));
        return a;
    }
}