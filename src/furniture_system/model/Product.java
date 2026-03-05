package furniture_system.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Maps to the Product table in the SQL Server schema.
 * Status values: ACTIVE | INACTIVE | OUT_OF_STOCK  (NVARCHAR CHECK constraint)
 */
public class Product {

    public enum Status {
        ACTIVE, INACTIVE, OUT_OF_STOCK;

        /** Display-friendly label for ComboBox */
        @Override
        public String toString() {
            return switch (this) {
                case ACTIVE        -> "Active";
                case INACTIVE      -> "Inactive";
                case OUT_OF_STOCK  -> "Out of Stock";
            };
        }

        /** Parse from DB string (e.g. "OUT_OF_STOCK") */
        public static Status fromDb(String s) {
            return switch (s.trim().toUpperCase()) {
                case "ACTIVE"        -> ACTIVE;
                case "INACTIVE"      -> INACTIVE;
                case "OUT_OF_STOCK"  -> OUT_OF_STOCK;
                default -> throw new IllegalArgumentException("Unknown product status: " + s);
            };
        }
    }

    // ── Fields matching DB columns ─────────────────────────────────────────
    private int           productId;       // IDENTITY(1,1)
    private String        name;            // NVARCHAR(150)
    private int           typeId;          // FK → FurnitureType
    private int           supplierId;      // FK → Supplier
    private BigDecimal    price;           // DECIMAL(15,2)
    private String        description;     // NVARCHAR(MAX), nullable
    private int           warrantyMonths;  // INT DEFAULT 0
    private Status        status;          // NVARCHAR(15) DEFAULT 'ACTIVE'
    private LocalDateTime createdAt;       // DATETIME2

    // ── JOIN display columns (not stored in Product table) ─────────────────
    private String typeName;
    private String supplierName;

    // ── Constructor ────────────────────────────────────────────────────────
    public Product() {
        this.warrantyMonths = 0;
        this.status         = Status.ACTIVE;
    }

    // ── Getters / Setters ──────────────────────────────────────────────────
    public int           getProductId()         { return productId; }
    public void          setProductId(int v)    { this.productId = v; }

    public String        getName()              { return name; }
    public void          setName(String v)      { this.name = v; }

    public int           getTypeId()            { return typeId; }
    public void          setTypeId(int v)       { this.typeId = v; }

    public int           getSupplierId()        { return supplierId; }
    public void          setSupplierId(int v)   { this.supplierId = v; }

    public BigDecimal    getPrice()             { return price; }
    public void          setPrice(BigDecimal v) { this.price = v; }

    public String        getDescription()               { return description; }
    public void          setDescription(String v)       { this.description = v; }

    public int           getWarrantyMonths()            { return warrantyMonths; }
    public void          setWarrantyMonths(int v)       { this.warrantyMonths = v; }

    public Status        getStatus()                    { return status; }
    public void          setStatus(Status v)            { this.status = v; }

    public LocalDateTime getCreatedAt()                 { return createdAt; }
    public void          setCreatedAt(LocalDateTime v)  { this.createdAt = v; }

    public String        getTypeName()                  { return typeName; }
    public void          setTypeName(String v)          { this.typeName = v; }

    public String        getSupplierName()              { return supplierName; }
    public void          setSupplierName(String v)      { this.supplierName = v; }

    @Override public String toString() { return name != null ? name : ""; }
}
