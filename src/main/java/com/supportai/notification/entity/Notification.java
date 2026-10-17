package com.supportai.notification.entity;

import com.supportai.ticket.entity.Ticket;
import com.supportai.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(
            User recipient,
            Ticket ticket,
            String type,
            String message
    ) {
        this.recipient = recipient;
        this.ticket = ticket;
        this.type = type;
        this.message = message;
    }

    public Long getId() {
        return id;
    }

    public User getRecipient() {
        return recipient;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public String getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public boolean isRead() {
        return read;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void markAsRead() {
        this.read = true;
    }
}
