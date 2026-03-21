package furniture_system.model;

/**
 * Maps to the Supplier table.
 * Used in ComboBox for product form.
 */
public class Supplier {
    private int    supplierId;   // IDENTITY(1,1)
    private String name;         // NVARCHAR(150)
    private String phone;        // NVARCHAR(15)
    private String email;        // NVARCHAR(100)
    private String address;      // NVARCHAR(255)
    private String status;       // NVARCHAR(10): ACTIVE | INACTIVE

    public Supplier() {}
    public Supplier(int id, String name) {
        this.supplierId = id;
        this.name       = name;
    }

    public int    getSupplierId()         { return supplierId; }
    public void   setSupplierId(int v)    { this.supplierId = v; }
    public String getName()               { return name; }
    public void   setName(String v)       { this.name = v; }
    public String getPhone()              { return phone; }
    public void   setPhone(String v)      { this.phone = v; }
    public String getEmail()              { return email; }
    public void   setEmail(String v)      { this.email = v; }
    public String getAddress()            { return address; }
    public void   setAddress(String v)    { this.address = v; }
    public String getStatus()             { return status; }
    public void   setStatus(String v)     { this.status = v; }

    /** ComboBox display */
    @Override public String toString() { return name != null ? name : ""; }
}
