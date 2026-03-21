package furniture_system.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Employee {

    public enum Position { MANAGER, SALES, WAREHOUSE, ACCOUNTANT, TECHNICIAN }
    public enum Status   { ACTIVE, INACTIVE, SUSPENDED }

    private int           employeeId;
    private String        fullName;
    private String        phone;
    private String        email;
    private String        address;
    private LocalDate     dob;
    private Position      position;
    private Status        status;
    private int           accountId;
    private LocalDateTime hiredAt;

    // ── linked account username (for display only, not persisted here) ────────
    private String        accountUsername;

    public Employee() {}

    // ── Getters & Setters ─────────────────────────────────────────────────────
    public int           getEmployeeId()                  { return employeeId; }
    public void          setEmployeeId(int v)             { this.employeeId = v; }

    public String        getFullName()                    { return fullName; }
    public void          setFullName(String v)            { this.fullName = v; }

    public String        getPhone()                       { return phone; }
    public void          setPhone(String v)               { this.phone = v; }

    public String        getEmail()                       { return email; }
    public void          setEmail(String v)               { this.email = v; }

    public String        getAddress()                     { return address; }
    public void          setAddress(String v)             { this.address = v; }

    public LocalDate     getDob()                         { return dob; }
    public void          setDob(LocalDate v)              { this.dob = v; }

    public Position      getPosition()                    { return position; }
    public void          setPosition(Position v)          { this.position = v; }

    public Status        getStatus()                      { return status; }
    public void          setStatus(Status v)              { this.status = v; }

    public int           getAccountId()                   { return accountId; }
    public void          setAccountId(int v)              { this.accountId = v; }

    public LocalDateTime getHiredAt()                     { return hiredAt; }
    public void          setHiredAt(LocalDateTime v)      { this.hiredAt = v; }

    public String        getAccountUsername()             { return accountUsername; }
    public void          setAccountUsername(String v)     { this.accountUsername = v; }
}
