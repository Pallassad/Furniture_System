package furniture_system.model;

import java.time.LocalDateTime;

/**
 * Maps to table: FurnitureType
 *   TypeId      INT IDENTITY(1,1) PK
 *   TypeName    NVARCHAR(100) NOT NULL UNIQUE  (LEN >= 2)
 *   Description NVARCHAR(MAX) NULL
 *   Status      NVARCHAR(10)  CHECK IN ('ACTIVE','INACTIVE')
 *   CreatedAt   DATETIME2     DEFAULT CURRENT_TIMESTAMP
 */
public class FurnitureType {

    private int           typeId;
    private String        typeName;
    private String        description;
    private String        status;
    private LocalDateTime createdAt;

    public FurnitureType() {}

    /** Full constructor – used when reading from DB */
    public FurnitureType(int typeId, String typeName, String description,
                         String status, LocalDateTime createdAt) {
        this.typeId      = typeId;
        this.typeName    = typeName;
        this.description = description;
        this.status      = status;
        this.createdAt   = createdAt;
    }

    /** Constructor for new records (typeId / createdAt not required) */
    public FurnitureType(String typeName, String description, String status) {
        this.typeName    = typeName;
        this.description = description;
        this.status      = status;
    }

    // ── Getters & Setters ────────────────────────────────────────────────────
    public int    getTypeId()                          { return typeId; }
    public void   setTypeId(int v)                     { this.typeId = v; }

    public String getTypeName()                        { return typeName; }
    public void   setTypeName(String v)                { this.typeName = v; }

    public String getDescription()                     { return description; }
    public void   setDescription(String v)             { this.description = v; }

    public String getStatus()                          { return status; }
    public void   setStatus(String v)                  { this.status = v; }

    public LocalDateTime getCreatedAt()                { return createdAt; }
    public void          setCreatedAt(LocalDateTime v) { this.createdAt = v; }

    /** Used for ComboBox display */
    @Override
    public String toString() { return typeName; }
}