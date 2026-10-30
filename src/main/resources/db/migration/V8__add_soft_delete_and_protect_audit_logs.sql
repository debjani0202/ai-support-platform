-- Adds soft deletion support to tickets to prevent loss of audit history.
ALTER TABLE tickets
    ADD COLUMN deleted_at TIMESTAMP;

-- Index to optimize active ticket filtering.
CREATE INDEX idx_tickets_deleted_at
    ON tickets(deleted_at);

-- Defense-in-depth: Reject hard deletions at database level if audit logs exist.
ALTER TABLE ticket_audit_logs
    DROP CONSTRAINT fk_audit_logs_ticket,
    ADD CONSTRAINT fk_audit_logs_ticket
        FOREIGN KEY (ticket_id)
        REFERENCES tickets(id)
        ON DELETE RESTRICT;
