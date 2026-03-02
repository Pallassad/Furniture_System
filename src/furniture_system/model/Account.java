package furniture_system.model;

import java.time.LocalDateTime;

/**
 * Account – mirrors the Account table in the database.
 */
public class Account {

    public enum Role   { ADMIN, EMPLOYEE }
    public enum Status { ACTIVE, INACTIVE, SUSPENDED }

    private int           accountId;
    private String        username;
    private String        passwordHash;
    private Role          role;
    private Status        status;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    private int           failedAttempts;

    // ── Constructors ──────────────────────────────────────────────────────────

    public Account() {}

    public Account(int accountId, String username, String passwordHash,
                   Role role, Status status,
                   LocalDateTime createdAt, LocalDateTime lastLoginAt,
                   int failedAttempts) {
        this.accountId      = accountId;
        this.username       = username;
        this.passwordHash   = passwordHash;
        this.role           = role;
        this.status         = status;
        this.createdAt      = createdAt;
        this.lastLoginAt    = lastLoginAt;
        this.failedAttempts = failedAttempts;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public int           getAccountId()      { return accountId; }
    public void          setAccountId(int v) { this.accountId = v; }

    public String        getUsername()           { return username; }
    public void          setUsername(String v)   { this.username = v; }

    public String        getPasswordHash()           { return passwordHash; }
    public void          setPasswordHash(String v)   { this.passwordHash = v; }

    public Role          getRole()           { return role; }
    public void          setRole(Role v)     { this.role = v; }

    public Status        getStatus()             { return status; }
    public void          setStatus(Status v)     { this.status = v; }

    public LocalDateTime getCreatedAt()              { return createdAt; }
    public void          setCreatedAt(LocalDateTime v) { this.createdAt = v; }

    public LocalDateTime getLastLoginAt()              { return lastLoginAt; }
    public void          setLastLoginAt(LocalDateTime v) { this.lastLoginAt = v; }

    public int           getFailedAttempts()      { return failedAttempts; }
    public void          setFailedAttempts(int v) { this.failedAttempts = v; }

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
