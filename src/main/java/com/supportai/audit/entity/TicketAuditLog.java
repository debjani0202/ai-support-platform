
package com.supportai.audit.entity;

import com.supportai.ticket.entity.Ticket;
import com.supportai.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents one audit record for an action performed on a ticket.
 * For example, a status change or an assignment.
 */
@Entity
@Table(name = "ticket_audit_logs")
public class TicketAuditLog {

    // Automatically generated primary key.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The ticket affected by this action.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    // The user who performed this action.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    // Examples: CREATED, STATUS_CHANGED, ASSIGNED.
    @Column(nullable = false, length = 50)
    private String action;

    // The value before the change, if applicable.
    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    // The value after the change, if applicable.
    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    // Time when this audit record was created.
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // JPA requires a no-argument constructor.
    public TicketAuditLog() {
    }

    // Creates a new audit record for a ticket action.
    public TicketAuditLog(
            Ticket ticket,
            User actor,
            String action,
            String oldValue,
            String newValue) {
        this.ticket = ticket;
        this.actor = actor;
        this.action = action;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    // Creates a new audit record with an explicit creation timestamp.
    public TicketAuditLog(
            Ticket ticket,
            User actor,
            String action,
            String oldValue,
            String newValue,
            LocalDateTime createdAt) {
        this(ticket, actor, action, oldValue, newValue);
        this.createdAt = createdAt;
    }

    // Set the timestamp before the entity is first saved.
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public User getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
