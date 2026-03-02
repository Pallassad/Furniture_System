package furniture_system.utils;

import furniture_system.model.Account;

/**
 * SessionManager – singleton that stores the currently authenticated account.
 * Must be cleared on logout.
 */
public final class SessionManager {

    private static SessionManager instance;
    private Account currentAccount;

    private SessionManager() {}

    public static SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    /** Store the successfully authenticated account. */
    public void login(Account account) {
        this.currentAccount = account;
    }

    /** Remove the session (logout). */
    public void logout() {
        this.currentAccount = null;
    }

    /** Returns the currently logged-in account, or null if not logged in. */
    public Account getCurrentAccount() {
        return currentAccount;
    }

    public boolean isLoggedIn() {
        return currentAccount != null;
    }

    public boolean isAdmin() {
        return isLoggedIn() && currentAccount.isAdmin();
    }
}
