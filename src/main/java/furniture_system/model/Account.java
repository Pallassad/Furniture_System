package furniture_system.model;

import java.time.LocalDateTime;

/**
 * Account – mirrors the Account table in the database.
 * UPDATED: createdAt removed; email added (used for forgot-password flow via Gmail).
 *          resetToken / resetTokenExpiry added for the forgot-password flow.
 */
public class Account {

    public enum Role   { ADMIN, EMPLOYEE }
    public enum Status { ACTIVE, INACTIVE, SUSPENDED }

    private int           accountId;
    private String        username;
    private String        passwordHash;
    private Role          role;
    private Status        status;
    private String        email;               // replaces createdAt
    private LocalDateTime lastLoginAt;
    private int           failedAttempts;
    private String        resetToken;          // 6-digit OTP; NULL when not in use
    private LocalDateTime resetTokenExpiry;    // OTP expiry timestamp

    // ── Constructors ──────────────────────────────────────────────────────────
    public Account() {}

    public Account(int accountId, String username, String passwordHash,
                   Role role, Status status,
                   String email, LocalDateTime lastLoginAt,
                   int failedAttempts) {
        this.accountId      = accountId;
        this.username       = username;
        this.passwordHash   = passwordHash;
        this.role           = role;
        this.status         = status;
        this.email          = email;
        this.lastLoginAt    = lastLoginAt;
        this.failedAttempts = failedAttempts;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────
    public int           getAccountId()                       { return accountId; }
    public void          setAccountId(int v)                  { this.accountId = v; }

    public String        getUsername()                        { return username; }
    public void          setUsername(String v)                { this.username = v; }

    public String        getPasswordHash()                    { return passwordHash; }
    public void          setPasswordHash(String v)            { this.passwordHash = v; }

    public Role          getRole()                            { return role; }
    public void          setRole(Role v)                      { this.role = v; }

    public Status        getStatus()                          { return status; }
    public void          setStatus(Status v)                  { this.status = v; }

    public String        getEmail()                           { return email; }
    public void          setEmail(String v)                   { this.email = v; }

    public LocalDateTime getLastLoginAt()                     { return lastLoginAt; }
    public void          setLastLoginAt(LocalDateTime v)      { this.lastLoginAt = v; }

    public int           getFailedAttempts()                  { return failedAttempts; }
    public void          setFailedAttempts(int v)             { this.failedAttempts = v; }

    public String        getResetToken()                      { return resetToken; }
    public void          setResetToken(String v)              { this.resetToken = v; }

    public LocalDateTime getResetTokenExpiry()                { return resetTokenExpiry; }
    public void          setResetTokenExpiry(LocalDateTime v) { this.resetTokenExpiry = v; }

    // ── Helpers ───────────────────────────────────────────────────────────────
    public boolean isActive()    { return status == Status.ACTIVE; }
    public boolean isSuspended() { return status == Status.SUSPENDED; }
    public boolean isAdmin()     { return role   == Role.ADMIN; }

    @Override
    public String toString() {
        return "Account{id=" + accountId + ", username='" + username +
               "', role=" + role + ", status=" + status + "}";
    }
}