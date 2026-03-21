package furniture_system.model;

import java.time.LocalDateTime;

/**
 * Represents one inventory movement in StockLog table.
 *
 * DB column mapping:
 *   LogId, ProductId, ChangeQty, LogType, RefId(nullable),
 *   Note(nullable), ActorId(FK->Employee), CreatedAt
 *
 * "loggedAt" field maps to DB column "CreatedAt".
 * "actorName" is a join field from Employee.FullName (not stored).
 */
public class StockLog {

    public enum LogType {
        IN, OUT, ADJUST, CANCEL, RETURN, WARRANTY_OUT, WARRANTY_IN
    }

    private int           logId;
    private int           productId;
    private int           changeQty;       // positive=increase, negative=decrease (DB CHECK: != 0)
    private LogType       logType;
    private Integer       refId;           // nullable – FK to OrderId or TicketId
    private String        note;            // nullable, max 255
    private int           actorId;         // FK -> Employee.EmployeeId
    private LocalDateTime loggedAt;        // maps to DB column "CreatedAt"

    // Denormalized display fields (populated by JOIN, not stored in this table)
    private String productName;
    private String actorName;             // from Employee.FullName

    public StockLog() {}

    /** Constructor for new log entries (before DB insert) */
    public StockLog(int productId, int changeQty, LogType logType,
                    String note, int actorId) {
        this.productId = productId;
        this.changeQty = changeQty;
        this.logType   = logType;
        this.note      = note;
        this.actorId   = actorId;
    }

    // ── Getters & Setters ──────────────────────────────────────────────────

    public int     getLogId()                { return logId; }
    public void    setLogId(int logId)       { this.logId = logId; }

    public int     getProductId()                  { return productId; }
    public void    setProductId(int productId)     { this.productId = productId; }

    public int     getChangeQty()                  { return changeQty; }
    public void    setChangeQty(int changeQty)     { this.changeQty = changeQty; }

    public LogType getLogType()                  { return logType; }
    public void    setLogType(LogType logType)   { this.logType = logType; }

    public Integer getRefId()                { return refId; }
    public void    setRefId(Integer refId)   { this.refId = refId; }

    public String  getNote()               { return note; }
    public void    setNote(String note)    { this.note = note; }

    public int     getActorId()                { return actorId; }
    public void    setActorId(int actorId)     { this.actorId = actorId; }

    /** Maps to DB column "CreatedAt" */
    public LocalDateTime getLoggedAt()                       { return loggedAt; }
    public void          setLoggedAt(LocalDateTime loggedAt) { this.loggedAt = loggedAt; }

    public String  getProductName()                      { return productName; }
    public void    setProductName(String productName)    { this.productName = productName; }

    /** From Employee.FullName via JOIN */
    public String  getActorName()                    { return actorName; }
    public void    setActorName(String actorName)    { this.actorName = actorName; }
}
