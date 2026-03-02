package furniture_system.service;

import furniture_system.model.Account;

/**
 * LoginResult – returned by AuthService.login() to communicate outcome.
 */
public class LoginResult {

    public enum Code {
        SUCCESS,
        INVALID_CREDENTIALS,   // username not found OR password wrong
        ACCOUNT_SUSPENDED,     // status = SUSPENDED
        ACCOUNT_INACTIVE,      // status = INACTIVE
        VALIDATION_ERROR       // blank input / too short
    }

    private final Code    code;
    private final String  message;
    private final Account account; // non-null only on SUCCESS

    private LoginResult(Code code, String message, Account account) {
        this.code    = code;
        this.message = message;
        this.account = account;
    }

    // ── Factory methods ───────────────────────────────────────────────────────

    public static LoginResult success(Account account) {
        return new LoginResult(Code.SUCCESS, "Login successful.", account);
    }

    public static LoginResult invalidCredentials() {
        return new LoginResult(Code.INVALID_CREDENTIALS, "Invalid username or password.", null);
    }

    public static LoginResult suspended(int remaining) {
        return new LoginResult(Code.ACCOUNT_SUSPENDED,
                "Account is suspended. Please contact the Administrator.", null);
    }

    public static LoginResult inactive() {
        return new LoginResult(Code.ACCOUNT_INACTIVE,
                "Account is inactive. Please contact the Administrator.", null);
    }

    public static LoginResult validationError(String msg) {
        return new LoginResult(Code.VALIDATION_ERROR, msg, null);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Code    getCode()    { return code; }
    public String  getMessage() { return message; }
    public Account getAccount() { return account; }
    public boolean isSuccess()  { return code == Code.SUCCESS; }
}
