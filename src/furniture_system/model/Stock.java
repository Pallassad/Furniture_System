package furniture_system.model;

import java.time.LocalDateTime;

/**
 * Maps to DB table: Stock
 * Columns: ProductId PK FK->Product, Quantity INT >= 0 default 0,
 *          ReorderLevel INT >= 0 default 5, LastUpdated DATETIME2
 *
 * productName is a denormalized display field populated by JOIN with Product.
 */
public class Stock {

    private int           productId;
    private int           quantity;
    private int           reorderLevel;
    private LocalDateTime lastUpdated;

    // Denormalized field populated by JOIN
    private String productName;

    public Stock() {}

    public Stock(int productId, int quantity, int reorderLevel, LocalDateTime lastUpdated) {
        this.productId    = productId;
        this.quantity     = quantity;
        this.reorderLevel = reorderLevel;
        this.lastUpdated  = lastUpdated;
    }

    public int           getProductId()               { return productId; }
    public void          setProductId(int v)           { this.productId = v; }
    public int           getQuantity()                 { return quantity; }
    public void          setQuantity(int v)            { this.quantity = v; }
    public int           getReorderLevel()             { return reorderLevel; }
    public void          setReorderLevel(int v)        { this.reorderLevel = v; }
    public LocalDateTime getLastUpdated()              { return lastUpdated; }
    public void          setLastUpdated(LocalDateTime v){ this.lastUpdated = v; }
    public String        getProductName()              { return productName; }
    public void          setProductName(String v)      { this.productName = v; }

    /** Returns true when current quantity is below the reorder threshold. */
    public boolean isBelowReorder() { return quantity < reorderLevel; }
}
