package furniture_system.config;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * DatabaseConfig — portable connection manager.
 *
 * Priority order for loading settings:
 *   1. External file:  <app-dir>/db.properties   (next to the JAR)
 *   2. Bundled file:   classpath:/furniture_system/db.properties
 *
 * If the connection test fails (or no settings exist), a JavaFX setup
 * dialog is shown so the user can enter the correct server name/credentials.
 * Confirmed settings are saved to the external file for future runs.
 */
public class DatabaseConfig {

    // ── External config location (same folder as the running JAR) ────────────
    private static final Path EXTERNAL_CONFIG = resolveExternalConfig();

    private static String url;
    private static String user;
    private static String password;

    // Prevent re-showing the dialog if already initialised successfully
    private static volatile boolean initialised = false;

    // ── Static init: load + test, show dialog if needed ──────────────────────
    static {
        loadProperties();
        if (!testConnection()) {
            showSetupDialog();
        }
        initialised = true;
    }

    private DatabaseConfig() {}

    // ─────────────────────────────────────────────────────────────────────────
    //  PUBLIC API
    // ─────────────────────────────────────────────────────────────────────────

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  LOAD PROPERTIES
    // ─────────────────────────────────────────────────────────────────────────

    private static void loadProperties() {
        Properties props = new Properties();

        // 1. Try external file first
        if (Files.exists(EXTERNAL_CONFIG)) {
            try (InputStream in = Files.newInputStream(EXTERNAL_CONFIG)) {
                props.load(in);
                applyProps(props);
                return;
            } catch (IOException ignored) {}
        }

        // 2. Fall back to bundled classpath resource
        try (InputStream in = DatabaseConfig.class
                .getClassLoader()
                .getResourceAsStream("furniture_system/db.properties")) {
            if (in != null) {
                props.load(in);
                applyProps(props);
            }
        } catch (IOException ignored) {}
    }

    private static void applyProps(Properties p) {
        url      = p.getProperty("db.url",      "");
        user     = p.getProperty("db.user",     "sa");
        password = p.getProperty("db.password", "");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CONNECTION TEST
    // ─────────────────────────────────────────────────────────────────────────

    private static boolean testConnection() {
        if (url == null || url.isBlank()) return false;
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            return c != null && !c.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SETUP DIALOG
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Shows a modal JavaFX dialog that lets the user configure the DB
     * connection. Blocks until a working connection is confirmed.
     * Must be called on (or marshalled to) the JavaFX Application Thread.
     */
    private static void showSetupDialog() {
        // Parse current URL to pre-fill fields
        String currentServer = extractServer(url);
        String currentDb     = extractDatabase(url);

        // Run on JavaFX thread if not already there
        if (!Platform.isFxApplicationThread()) {
            AtomicBoolean done = new AtomicBoolean(false);
            Platform.runLater(() -> {
                showDialogInternal(currentServer, currentDb);
                done.set(true);
            });
            // Busy-wait (only at startup)
            while (!done.get()) {
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            }
        } else {
            showDialogInternal(currentServer, currentDb);
        }
    }

    private static void showDialogInternal(String currentServer, String currentDb) {
        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle("Database Connection Setup");
        dlg.setResizable(false);

        // ── Fields ──────────────────────────────────────────────────────────
        TextField   tfServer   = new TextField(currentServer);
        TextField   tfPort     = new TextField("1433");
        TextField   tfDatabase = new TextField(currentDb.isBlank() ? "furniture_system" : currentDb);
        TextField   tfUser     = new TextField(user != null ? user : "sa");
        PasswordField pfPassword = new PasswordField();
        if (password != null) pfPassword.setText(password);

        tfServer.setPromptText("e.g. localhost  or  PC-NAME\\SQLEXPRESS");
        tfPort.setPromptText("1433");

        Label lblStatus = new Label("Enter your SQL Server connection details.");
        lblStatus.setStyle("-fx-text-fill:#555; -fx-font-size:12px;");
        lblStatus.setWrapText(true);

        // ── Layout ──────────────────────────────────────────────────────────
        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(10);
        grid.setPadding(new Insets(24));
        grid.getColumnConstraints().addAll(
            new ColumnConstraints(130),
            new ColumnConstraints(280)
        );

        int row = 0;
        Label title = new Label("🗄  Database Connection");
        title.setStyle("-fx-font-size:15px; -fx-font-weight:bold; -fx-text-fill:#1a237e;");
        grid.add(title, 0, row, 2, 1); row++;
        grid.add(new Separator(), 0, row, 2, 1); row++;

        grid.add(bold("Server / Host *"),  0, row); grid.add(tfServer,   1, row++);
        grid.add(hint("e.g. localhost, DESKTOP-XYZ, PC\\SQLEXPRESS"), 1, row); row++;
        grid.add(bold("Port"),             0, row); grid.add(tfPort,     1, row++);
        grid.add(bold("Database Name *"),  0, row); grid.add(tfDatabase, 1, row++);
        grid.add(bold("Username *"),       0, row); grid.add(tfUser,     1, row++);
        grid.add(bold("Password"),         0, row); grid.add(pfPassword, 1, row++);
        grid.add(new Separator(), 0, row, 2, 1); row++;
        grid.add(lblStatus, 0, row, 2, 1); row++;

        Button btnTest = new Button("🔌  Test & Save");
        btnTest.setStyle("-fx-background-color:#1a237e; -fx-text-fill:white;" +
                         "-fx-background-radius:6; -fx-padding:8 20; -fx-font-weight:bold;");
        HBox btns = new HBox(btnTest);
        btns.setAlignment(Pos.CENTER_RIGHT);
        grid.add(btns, 0, row, 2, 1);

        // ── Test & Save action ───────────────────────────────────────────────
        btnTest.setOnAction(ev -> {
            String server   = tfServer.getText().trim();
            String port     = tfPort.getText().trim().isEmpty() ? "1433" : tfPort.getText().trim();
            String database = tfDatabase.getText().trim();
            String u        = tfUser.getText().trim();
            String p        = pfPassword.getText();

            if (server.isEmpty() || database.isEmpty() || u.isEmpty()) {
                setStatus(lblStatus, "❌  Server, Database and Username are required.", "#c62828");
                return;
            }

            // Build JDBC URL for SQL Server
            String testUrl = "jdbc:sqlserver://" + server + ":" + port
                    + ";databaseName=" + database
                    + ";encrypt=true;trustServerCertificate=true;loginTimeout=10;";

            btnTest.setDisable(true);
            btnTest.setText("Testing…");

            // Test in background to keep UI responsive
            Thread t = new Thread(() -> {
                boolean ok = false;
                String errMsg = "";
                try (Connection c = DriverManager.getConnection(testUrl, u, p)) {
                    ok = c != null && !c.isClosed();
                } catch (SQLException ex) {
                    errMsg = ex.getMessage();
                }

                final boolean success = ok;
                final String  err     = errMsg;

                Platform.runLater(() -> {
                    btnTest.setDisable(false);
                    btnTest.setText("🔌  Test & Save");
                    if (success) {
                        // Apply + persist
                        url      = testUrl;
                        user     = u;
                        password = p;
                        saveExternalConfig(testUrl, u, p);
                        setStatus(lblStatus,
                            "✅  Connected! Settings saved to db.properties.", "#2e7d32");
                        // Close after short delay so user can read the message
                        new Thread(() -> {
                            try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
                            Platform.runLater(dlg::close);
                        }).start();
                    } else {
                        setStatus(lblStatus,
                            "❌  Connection failed: " + err, "#c62828");
                    }
                });
            });
            t.setDaemon(true);
            t.start();
        });

        dlg.setScene(new Scene(grid));
        dlg.showAndWait();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  PERSIST TO EXTERNAL FILE
    // ─────────────────────────────────────────────────────────────────────────

    private static void saveExternalConfig(String dbUrl, String dbUser, String dbPass) {
        Properties props = new Properties();
        props.setProperty("db.url",      dbUrl);
        props.setProperty("db.user",     dbUser);
        props.setProperty("db.password", dbPass);
        try (OutputStream out = Files.newOutputStream(EXTERNAL_CONFIG,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            props.store(out, "Fair Deal Furniture — DB connection (auto-saved)");
        } catch (IOException e) {
            System.err.println("Could not save db.properties: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /** Resolves <jar-directory>/db.properties */
    private static Path resolveExternalConfig() {
        try {
            // Works both when run from IDE and from a packaged JAR
            Path jarDir = Path.of(
                DatabaseConfig.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()
            ).getParent();
            return jarDir.resolve("db.properties");
        } catch (Exception e) {
            // Fallback: current working directory
            return Path.of("db.properties");
        }
    }

    /** Extracts host:port portion from a JDBC SQL Server URL. */
    private static String extractServer(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) return "";
        try {
            // jdbc:sqlserver://HOST:PORT;...
            String after = jdbcUrl.replace("jdbc:sqlserver://", "");
            String hostPort = after.split(";")[0];
            // Remove port part — return just host
            return hostPort.contains(":") ? hostPort.split(":")[0] : hostPort;
        } catch (Exception e) {
            return "";
        }
    }

    /** Extracts databaseName value from a JDBC SQL Server URL. */
    private static String extractDatabase(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) return "";
        for (String part : jdbcUrl.split(";")) {
            if (part.toLowerCase().startsWith("databasename=")) {
                return part.substring("databasename=".length());
            }
        }
        return "";
    }

    private static void setStatus(Label lbl, String msg, String color) {
        lbl.setText(msg);
        lbl.setStyle("-fx-text-fill:" + color + "; -fx-font-size:12px;");
    }

    private static Label bold(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-font-weight:bold; -fx-font-size:12px;");
        return l;
    }

    private static Label hint(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-font-size:10px; -fx-text-fill:#888;");
        return l;
    }
}