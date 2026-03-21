package furniture_system.model;

import java.math.BigDecimal;

public class OrderLine {

    private int lineId;
    private int orderId;
    private int productId;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;   // computed column – read-only from DB

    // display helper
    private String productName;

    public OrderLine() {}

    public OrderLine(int orderId, int productId, int quantity, BigDecimal unitPrice) {
        this.orderId   = orderId;
        this.productId = productId;
        this.quantity  = quantity;
        this.unitPrice = unitPrice;
        this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    // ── getters / setters ────────────────────────────────────────────────

    public int getLineId()                     { return lineId; }
    public void setLineId(int v)               { this.lineId = v; }

    public int getOrderId()                    { return orderId; }
    public void setOrderId(int v)              { this.orderId = v; }

    public int getProductId()                  { return productId; }
    public void setProductId(int v)            { this.productId = v; }

    public int getQuantity()                   { return quantity; }
    public void setQuantity(int v)             { this.quantity = v; }

    public BigDecimal getUnitPrice()           { return unitPrice; }
    public void setUnitPrice(BigDecimal v)     { this.unitPrice = v; }

    public BigDecimal getLineTotal()           { return lineTotal; }
    public void setLineTotal(BigDecimal v)     { this.lineTotal = v; }

    public String getProductName()             { return productName; }
    public void setProductName(String v)       { this.productName = v; }
}
