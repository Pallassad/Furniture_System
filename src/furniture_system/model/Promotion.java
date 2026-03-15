package furniture_system.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Promotion {

    private int promoId;
    private String code;
    private String name;
    private String discountType;   // PERCENT | FIXED
    private BigDecimal discountValue;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private BigDecimal minOrderValue;
    private Integer usageLimit;    // nullable
    private int usedCount;
    private String status;         // UPCOMING | ACTIVE | EXPIRED | DISABLED
    private LocalDateTime createdAt;

    public Promotion() {}

    // ── getters / setters ────────────────────────────────────────────────

    public int getPromoId()                         { return promoId; }
    public void setPromoId(int v)                   { this.promoId = v; }

    public String getCode()                         { return code; }
    public void setCode(String v)                   { this.code = v; }

    public String getName()                         { return name; }
    public void setName(String v)                   { this.name = v; }

    public String getDiscountType()                 { return discountType; }
    public void setDiscountType(String v)           { this.discountType = v; }

    public BigDecimal getDiscountValue()            { return discountValue; }
    public void setDiscountValue(BigDecimal v)      { this.discountValue = v; }

    public LocalDateTime getStartDate()             { return startDate; }
    public void setStartDate(LocalDateTime v)       { this.startDate = v; }

    public LocalDateTime getEndDate()               { return endDate; }
    public void setEndDate(LocalDateTime v)         { this.endDate = v; }

    public BigDecimal getMinOrderValue()            { return minOrderValue; }
    public void setMinOrderValue(BigDecimal v)      { this.minOrderValue = v; }

    public Integer getUsageLimit()                  { return usageLimit; }
    public void setUsageLimit(Integer v)            { this.usageLimit = v; }

    public int getUsedCount()                       { return usedCount; }
    public void setUsedCount(int v)                 { this.usedCount = v; }

    public String getStatus()                       { return status; }
    public void setStatus(String v)                 { this.status = v; }

    public LocalDateTime getCreatedAt()             { return createdAt; }
    public void setCreatedAt(LocalDateTime v)       { this.createdAt = v; }

    @Override
    public String toString() { return code + " – " + name; }
}
