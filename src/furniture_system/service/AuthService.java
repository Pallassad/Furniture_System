package furniture_system.service;

import furniture_system.dao.AccountDAO;
import furniture_system.model.Account;
import furniture_system.model.Account.Role;
import furniture_system.model.Account.Status;
import furniture_system.utils.PasswordUtils;
import furniture_system.utils.SessionManager;

import java.util.List;

public class AuthService {

    private final AccountDAO accountDAO;

    public AuthService() {
        this.accountDAO = new AccountDAO();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3.1 / 4.1 – LOGIN
    // ─────────────────────────────────────────────────────────────────────────

    public LoginResult login(String username, String password) {
        if (username == null || username.isBlank())
            return LoginResult.validationError("Username cannot be empty.");
        if (username.trim().length() < 3)
            return LoginResult.validationError("Username must be at least 3 characters.");
        if (password == null || password.isBlank())
            return LoginResult.validationError("Password cannot be empty.");

        Account account = accountDAO.findByUsername(username.trim());
        if (account == null) return LoginResult.invalidCredentials();

        if (account.getStatus() == Status.SUSPENDED) return LoginResult.suspended(0);
        if (account.getStatus() == Status.INACTIVE)  return LoginResult.inactive();

        if (!PasswordUtils.verify(password, account.getPasswordHash())) {
            accountDAO.recordFailedLogin(account.getAccountId());
            Account updated = accountDAO.findById(account.getAccountId());
            if (updated != null && updated.getStatus() == Status.SUSPENDED)
                return LoginResult.suspended(0);
            return LoginResult.invalidCredentials();
        }

        accountDAO.recordSuccessfulLogin(account.getAccountId());
        account.setLastLoginAt(java.time.LocalDateTime.now());
        account.setFailedAttempts(0);
        SessionManager.getInstance().login(account);
        return LoginResult.success(account);
    }

    public void logout() {
        SessionManager.getInstance().logout();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3.2 – AUTH & AUTHORIZATION (Admin only)
    // ─────────────────────────────────────────────────────────────────────────

    public List<Account> getAllAccounts() {
        requireAdmin();
        return accountDAO.findAll();
    }

    // ── ADD ───────────────────────────────────────────────────────────────────
    /**
     * Creates a new Account.
     * Validates: username ≥ 3 chars, not duplicate, password ≥ 6 chars.
     * Returns generated AccountId.
     */
    public int addAccount(String username, String plainPassword, Role role, Status status) {
        requireAdmin();

        // Validate username
        if (username == null || username.isBlank())
            throw new IllegalArgumentException("Username is required.");
        if (username.trim().length() < 3)
            throw new IllegalArgumentException("Username must be at least 3 characters.");
        if (username.trim().length() > 50)
            throw new IllegalArgumentException("Username must be ≤ 50 characters.");
        if (!username.trim().matches("[a-zA-Z0-9_]+"))
            throw new IllegalArgumentException("Username may only contain letters, digits, and underscores.");
        if (accountDAO.isUsernameTaken(username.trim(), -1))
            throw new IllegalArgumentException("Username already exists.");

        // Validate password
        if (plainPassword == null || plainPassword.isBlank())
            throw new IllegalArgumentException("Password is required.");
        if (plainPassword.length() < 6)
            throw new IllegalArgumentException("Password must be at least 6 characters.");

        if (role   == null) throw new IllegalArgumentException("Role is required.");
        if (status == null) throw new IllegalArgumentException("Status is required.");

        String passwordHash = PasswordUtils.hash(plainPassword);
        return accountDAO.insert(username.trim(), passwordHash, role, status);
    }

    // ── DELETE ────────────────────────────────────────────────────────────────
    /**
     * Deletes an account permanently.
     * Rules:
     *   - Cannot delete your own account.
     *   - Cannot delete if account is linked to an Employee record.
     */
    public boolean deleteAccount(int accountId) {
        requireAdmin();
        Account current = SessionManager.getInstance().getCurrentAccount();
        if (current.getAccountId() == accountId)
            throw new IllegalStateException("You cannot delete your own account.");
        if (accountDAO.isLinkedToEmployee(accountId))
            throw new IllegalStateException(
                "This account is linked to an Employee record.\n" +
                "Remove or reassign the Employee first.");
        return accountDAO.delete(accountId);
    }

    // ── EXISTING METHODS (unchanged) ──────────────────────────────────────────

    // ── RENAME / CHANGE PASSWORD ───────────────────────────────────────────────

    /**
     * Updates the username of an account.
     * Rules: ≥ 3 chars, [a-zA-Z0-9_] only, not duplicate.
     */
    public boolean updateUsername(int accountId, String newUsername) {
        requireAdmin();
        if (newUsername == null || newUsername.isBlank())
            throw new IllegalArgumentException("Username is required.");
        if (newUsername.trim().length() < 3)
            throw new IllegalArgumentException("Username must be at least 3 characters.");
        if (newUsername.trim().length() > 50)
            throw new IllegalArgumentException("Username must be ≤ 50 characters.");
        if (!newUsername.trim().matches("[a-zA-Z0-9_]+"))
            throw new IllegalArgumentException("Username may only contain letters, digits, and underscores.");
        if (accountDAO.isUsernameTaken(newUsername.trim(), accountId))
            throw new IllegalArgumentException("Username already exists.");
        return accountDAO.updateUsername(accountId, newUsername.trim());
    }

    /**
     * Resets/changes password for an account.
     * Admin can reset any account's password. ≥ 6 chars required.
     */
    public boolean updatePassword(int accountId, String newPlainPassword) {
        requireAdmin();
        if (newPlainPassword == null || newPlainPassword.isBlank())
            throw new IllegalArgumentException("Password is required.");
        if (newPlainPassword.length() < 6)
            throw new IllegalArgumentException("Password must be at least 6 characters.");
        String hash = PasswordUtils.hash(newPlainPassword);
        return accountDAO.updatePassword(accountId, hash);
    }

    public boolean updateAccountStatus(int accountId, Status newStatus) {
        requireAdmin();
        if (newStatus == null) throw new IllegalArgumentException("Status cannot be null");
        return accountDAO.updateStatus(accountId, newStatus);
    }

    public boolean updateAccountRole(int accountId, Role newRole) {
        requireAdmin();
        Account current = SessionManager.getInstance().getCurrentAccount();
        if (current.getAccountId() == accountId)
            throw new IllegalStateException("Admin cannot change their own role.");
        return accountDAO.updateRole(accountId, newRole);
    }

    public boolean resetFailedAttempts(int accountId) {
        requireAdmin();
        return accountDAO.resetFailedAttempts(accountId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GUARD
    // ─────────────────────────────────────────────────────────────────────────

    private void requireAdmin() {
        if (!SessionManager.getInstance().isAdmin())
            throw new SecurityException("Access denied: Admin privileges required.");
    }
}