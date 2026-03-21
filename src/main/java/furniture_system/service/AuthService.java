package furniture_system.service;

import furniture_system.dao.AccountDAO;
import furniture_system.model.Account;
import furniture_system.model.Account.Role;
import furniture_system.model.Account.Status;
import furniture_system.utils.PasswordUtils;
import furniture_system.utils.SessionManager;

import java.time.LocalDateTime;
import java.util.List;

public class AuthService {

    private final AccountDAO   accountDAO   = new AccountDAO();
    private final EmailService emailService = new EmailService();

    // LOGIN
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
        account.setLastLoginAt(LocalDateTime.now());
        account.setFailedAttempts(0);
        SessionManager.getInstance().login(account);
        return LoginResult.success(account);
    }

    public void logout() { SessionManager.getInstance().logout(); }

    // FORGOT PASSWORD
    public boolean requestPasswordReset(String email) {
        if (email == null || email.isBlank())
            throw new IllegalArgumentException("Email cannot be empty.");
        Account account = accountDAO.findByEmail(email.trim().toLowerCase());
        if (account == null) return false;
        String otp = String.format("%06d", (int)(Math.random() * 1000000));
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(15);
        accountDAO.saveResetToken(account.getAccountId(), otp, expiry);
        emailService.sendPasswordResetEmail(account.getEmail(), account.getUsername(), otp);
        return true;
    }

    public boolean resetPasswordWithToken(String token, String newPassword, String confirmPassword) {
        if (token == null || token.isBlank())
            throw new IllegalArgumentException("Reset code cannot be empty.");
        if (newPassword == null || newPassword.length() < 6)
            throw new IllegalArgumentException("Password must be at least 6 characters.");
        if (!newPassword.equals(confirmPassword))
            throw new IllegalArgumentException("Passwords do not match.");
        Account account = accountDAO.findByValidResetToken(token.trim());
        if (account == null)
            throw new IllegalArgumentException("Reset code is invalid or has expired.");
        accountDAO.updatePassword(account.getAccountId(), PasswordUtils.hash(newPassword));
        accountDAO.clearResetToken(account.getAccountId());
        return true;
    }

    // ADMIN CRUD
    public List<Account> getAllAccounts() { requireAdmin(); return accountDAO.findAll(); }

    public int addAccount(String username, String pwd, Role role, Status status, String email) {
        requireAdmin();
        validateUsername(username, -1);
        if (pwd == null || pwd.isBlank()) throw new IllegalArgumentException("Password is required.");
        if (pwd.length() < 6) throw new IllegalArgumentException("Password must be at least 6 characters.");
        if (role == null)   throw new IllegalArgumentException("Role is required.");
        if (status == null) throw new IllegalArgumentException("Status is required.");
        return accountDAO.insert(username.trim(), PasswordUtils.hash(pwd), role, status, validateEmail(email, -1));
    }

    public boolean deleteAccount(int accountId) {
        requireAdmin();
        Account me = SessionManager.getInstance().getCurrentAccount();
        if (me.getAccountId() == accountId)
            throw new IllegalStateException("You cannot delete your own account.");
        if (accountDAO.isLinkedToEmployee(accountId))
            throw new IllegalStateException("Account is linked to an Employee. Remove Employee first.");
        return accountDAO.delete(accountId);
    }

    public boolean updateUsername(int id, String v) {
        requireAdmin(); validateUsername(v, id);
        return accountDAO.updateUsername(id, v.trim());
    }
    public boolean updatePassword(int id, String v) {
        requireAdmin();
        if (v == null || v.length() < 6) throw new IllegalArgumentException("Password must be at least 6 characters.");
        return accountDAO.updatePassword(id, PasswordUtils.hash(v));
    }
    public boolean updateEmail(int id, String v) { requireAdmin(); return accountDAO.updateEmail(id, validateEmail(v, id)); }
    public boolean updateAccountStatus(int id, Status v) { requireAdmin(); return accountDAO.updateStatus(id, v); }
    public boolean updateAccountRole(int id, Role v) {
        requireAdmin();
        if (SessionManager.getInstance().getCurrentAccount().getAccountId() == id)
            throw new IllegalStateException("Admin cannot change their own role.");
        return accountDAO.updateRole(id, v);
    }
    public boolean resetFailedAttempts(int id) { requireAdmin(); return accountDAO.resetFailedAttempts(id); }

    private void validateUsername(String u, int excludeId) {
        if (u == null || u.isBlank()) throw new IllegalArgumentException("Username is required.");
        String t = u.trim();
        if (t.length() < 3)  throw new IllegalArgumentException("Username must be at least 3 characters.");
        if (t.length() > 50) throw new IllegalArgumentException("Username must be <= 50 characters.");
        if (!t.matches("[a-zA-Z0-9_]+"))
            throw new IllegalArgumentException("Username may only contain letters, digits, and underscores.");
        if (accountDAO.isUsernameTaken(t, excludeId)) throw new IllegalArgumentException("Username already exists.");
    }
    private String validateEmail(String email, int excludeId) {
        if (email == null || email.isBlank()) return null;
        String c = email.trim().toLowerCase();
        if (accountDAO.isEmailTaken(c, excludeId)) throw new IllegalArgumentException("Email is already used by another account.");
        return c;
    }
    private void requireAdmin() {
        if (!SessionManager.getInstance().isAdmin())
            throw new SecurityException("Access denied: Admin privileges required.");
    }
}