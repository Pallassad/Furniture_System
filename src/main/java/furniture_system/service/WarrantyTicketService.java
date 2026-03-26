package furniture_system.service;

import furniture_system.dao.WarrantyTicketDAO;
import furniture_system.model.WarrantyTicket;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service layer for WarrantyTicket.
 *
 * Responsibilities:
 *  • All business-rule validation (status flow, cost, terminal states…)
 *  • Delegates pure SQL operations to WarrantyTicketDAO
 *  • Controllers call ONLY this class — never the DAO directly
 *
 * Status flow:
 *   CREATED → RECEIVED → PROCESSING → WAITING_PART → COMPLETED
 *   Any non-terminal status → CANCELLED | REJECTED  (terminal)
 */
public class WarrantyTicketService {

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final Set<String> VALID_STATUSES = Set.of(
            "CREATED", "RECEIVED", "PROCESSING", "WAITING_PART",
            "COMPLETED", "CANCELLED", "REJECTED"
    );

    private static final Set<String> TERMINAL_STATUSES = Set.of(
            "COMPLETED", "CANCELLED", "REJECTED"
    );

    /**
     * Allowed forward transitions per status.
     * Terminal statuses map to empty sets — no further transitions allowed.
     */
    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
        "CREATED",      Set.of("RECEIVED",    "CANCELLED", "REJECTED"),
        "RECEIVED",     Set.of("PROCESSING",  "CANCELLED", "REJECTED"),
        "PROCESSING",   Set.of("WAITING_PART","COMPLETED", "CANCELLED", "REJECTED"),
        "WAITING_PART", Set.of("PROCESSING",  "COMPLETED", "CANCELLED", "REJECTED"),
        "COMPLETED",    Set.of(),
        "CANCELLED",    Set.of(),
        "REJECTED",     Set.of()
    );

    // ── Constructor ───────────────────────────────────────────────────────────

    private final WarrantyTicketDAO dao;

    public WarrantyTicketService() {
        this.dao = new WarrantyTicketDAO();
    }

    // ── READ ──────────────────────────────────────────────────────────────────

    public List<WarrantyTicket> getAll() throws SQLException {
        return dao.getAll();
    }

    /** Employee actor: only tickets assigned to this employee. */
    public List<WarrantyTicket> getByHandler(int handlerEmployeeId) throws SQLException {
        return dao.getByHandler(handlerEmployeeId);
    }

    public List<WarrantyTicket> search(String keyword) throws SQLException {
        if (keyword == null || keyword.isBlank()) return dao.getAll();
        return dao.search(keyword.trim());
    }

    public WarrantyTicket getById(int ticketId) throws SQLException {
        return dao.getById(ticketId);
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    /**
     * Validate then insert a new WarrantyTicket.
     * Status is always forced to CREATED regardless of what caller passes.
     *
     * @return generated TicketId
     * @throws IllegalArgumentException on business-rule violations
     * @throws SQLException             on DB errors (e.g. Order not COMPLETED)
     */
    public int create(WarrantyTicket t) throws SQLException {
        validateForCreate(t);
        t.setStatus("CREATED");
        if (t.getCost() == null) t.setCost(BigDecimal.ZERO);
        return dao.insert(t);
    }

    // ── UPDATE (Admin — full update) ──────────────────────────────────────────

    /**
     * Admin full update.
     * Validates status transition and immutability of terminal tickets.
     *
     * @throws IllegalArgumentException on bad input / invalid transition
     * @throws IllegalStateException    if ticket is already terminal
     * @throws SQLException             on DB errors
     */
    public int update(WarrantyTicket t) throws SQLException {
        WarrantyTicket existing = requireExists(t.getTicketId());
        requireNotTerminal(existing, "update");
        validateStatusTransition(existing.getStatus(), t.getStatus());
        validateIssueDesc(t.getIssueDesc());
        validateCost(t.getCost());
        return dao.update(t);
    }

    // ── UPDATE (Employee — status / cost / note only) ─────────────────────────

    /**
     * Employee lightweight update: only Status, Cost, Note.
     * Also verifies the requesting employee is the assigned handler.
     *
     * @throws IllegalArgumentException on bad input / invalid transition
     * @throws IllegalStateException    if ticket is terminal or employee is not handler
     * @throws SQLException             on DB errors
     */
    public int updateStatusCostNote(int ticketId, int requestingEmployeeId,
                                    String newStatus, BigDecimal cost,
                                    String note) throws SQLException {
        WarrantyTicket existing = requireExists(ticketId);
        requireNotTerminal(existing, "update");
        requireIsHandler(existing, requestingEmployeeId);
        validateStatusTransition(existing.getStatus(), newStatus);
        validateCost(cost);
        BigDecimal safeCost = (cost == null) ? BigDecimal.ZERO : cost;
        return dao.updateStatusCostNote(ticketId, newStatus, safeCost, note);
    }

    // ── CANCEL / REJECT ───────────────────────────────────────────────────────

    /** Admin: cancel a ticket → status CANCELLED. */
    public int cancel(int ticketId) throws SQLException {
        requireNotTerminal(requireExists(ticketId), "cancel");
        return dao.cancel(ticketId, "CANCELLED");
    }

    /** Admin: reject a ticket → status REJECTED. */
    public int reject(int ticketId) throws SQLException {
        requireNotTerminal(requireExists(ticketId), "reject");
        return dao.cancel(ticketId, "REJECTED");
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    /**
     * Hard-delete a WarrantyTicket.
     * Only allowed when Status is terminal: COMPLETED, CANCELLED, or REJECTED.
     *
     * @throws IllegalStateException if ticket is not terminal
     */
    public void delete(int ticketId) throws SQLException {
        WarrantyTicket existing = requireExists(ticketId);
        if (!TERMINAL_STATUSES.contains(existing.getStatus()))
            throw new IllegalStateException(
                "Can only delete warranty tickets with COMPLETED, CANCELLED, or REJECTED status. " +
                "Current status: " + existing.getStatus());
        if (dao.hardDelete(ticketId) == 0)
            throw new SQLException("Failed to delete warranty ticket #" + ticketId + ".");
    }

    // ── PRIVATE VALIDATORS ────────────────────────────────────────────────────

    private void validateForCreate(WarrantyTicket t) {
        if (t.getOrderId() <= 0)
            throw new IllegalArgumentException("OrderId is required.");
        if (t.getProductId() <= 0)
            throw new IllegalArgumentException("ProductId is required.");
        if (t.getCustomerId() <= 0)
            throw new IllegalArgumentException("CustomerId is required.");
        validateIssueDesc(t.getIssueDesc());
        validateCost(t.getCost());
    }

    private void validateIssueDesc(String issueDesc) {
        if (issueDesc == null || issueDesc.isBlank())
            throw new IllegalArgumentException("Issue description cannot be blank.");
        if (issueDesc.length() > 1000)
            throw new IllegalArgumentException("Issue description must be ≤ 1000 characters.");
    }

    private void validateCost(BigDecimal cost) {
        if (cost != null && cost.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Cost cannot be negative.");
    }

    private void validateStatusTransition(String current, String next) {
        if (next == null || !VALID_STATUSES.contains(next))
            throw new IllegalArgumentException("Invalid status: " + next);
        Set<String> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(next))
            throw new IllegalArgumentException(
                    "Cannot transition status from " + current + " → " + next + ".");
    }

    private WarrantyTicket requireExists(int ticketId) throws SQLException {
        WarrantyTicket t = dao.getById(ticketId);
        if (t == null)
            throw new IllegalArgumentException(
                    "Warranty ticket #" + ticketId + " not found.");
        return t;
    }

    private void requireNotTerminal(WarrantyTicket t, String action) {
        if (TERMINAL_STATUSES.contains(t.getStatus()))
            throw new IllegalStateException(
                    "Cannot " + action + " ticket #" + t.getTicketId()
                    + " — status is already " + t.getStatus() + ".");
    }

    private void requireIsHandler(WarrantyTicket t, int employeeId) {
        if (t.getHandlerEmployeeId() == null
                || t.getHandlerEmployeeId() != employeeId)
            throw new IllegalStateException(
                    "You are not the assigned handler for ticket #"
                    + t.getTicketId() + ".");
    }
}