package furniture_system.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Maps to DB table: SupplierProduct
 * Composite PK: SupplierId + ProductId
 * Columns: SupplierId FK->Supplier, ProductId FK->Product,
 *          ImportPrice DECIMAL(15,2) > 0,
 *          LeadTimeDays INT >= 0 default 0,
 *          UpdatedAt DATETIME2
 *
 * supplierName / productName are denormalized display fields populated by JOIN.
 */
public class SupplierProduct {

    private int           supplierId;
    private int           productId;
    private BigDecimal    importPrice;    // DB CHECK: > 0
    private int           leadTimeDays;  // DB CHECK: >= 0
    private LocalDateTime updatedAt;

    // Denormalized display fields (not stored in this table)
    private String supplierName;
    private String productName;

    public SupplierProduct() {}

    public SupplierProduct(int supplierId, int productId,
                           BigDecimal importPrice, int leadTimeDays) {
        this.supplierId   = supplierId;
        this.productId    = productId;
        this.importPrice  = importPrice;
        this.leadTimeDays = leadTimeDays;
    }

    public int           getSupplierId()              { return supplierId; }
    public void          setSupplierId(int v)          { this.supplierId = v; }
    public int           getProductId()               { return productId; }
    public void          setProductId(int v)           { this.productId = v; }
    public BigDecimal    getImportPrice()              { return importPrice; }
    public void          setImportPrice(BigDecimal v)  { this.importPrice = v; }
    public int           getLeadTimeDays()             { return leadTimeDays; }
    public void          setLeadTimeDays(int v)        { this.leadTimeDays = v; }
    public LocalDateTime getUpdatedAt()                { return updatedAt; }
    public void          setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
    public String        getSupplierName()             { return supplierName; }
    public void          setSupplierName(String v)     { this.supplierName = v; }
    public String        getProductName()              { return productName; }
    public void          setProductName(String v)      { this.productName = v; }
}
