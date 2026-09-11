ALTER TABLE tickets
    ADD COLUMN created_by BIGINT,
    ADD COLUMN assigned_to BIGINT,
    ADD COLUMN category VARCHAR(50),
    ADD COLUMN priority VARCHAR(20);

ALTER TABLE tickets
    ADD CONSTRAINT fk_tickets_created_by
        FOREIGN KEY (created_by)
        REFERENCES users(id);

ALTER TABLE tickets
    ADD CONSTRAINT fk_tickets_assigned_to
        FOREIGN KEY (assigned_to)
        REFERENCES users(id);

ALTER TABLE tickets
    ADD CONSTRAINT chk_tickets_category
        CHECK (
            category IS NULL
            OR category IN (
                'ACCOUNT_ACCESS',
                'PAYMENT',
                'TECHNICAL_ISSUE',
                'BUG_REPORT',
                'FEATURE_REQUEST',
                'GENERAL_QUERY'
            )
        );

ALTER TABLE tickets
    ADD CONSTRAINT chk_tickets_priority
        CHECK (
            priority IS NULL
            OR priority IN (
                'LOW',
                'MEDIUM',
                'HIGH',
                'URGENT'
            )
        );

CREATE INDEX idx_tickets_created_by
    ON tickets(created_by);

CREATE INDEX idx_tickets_assigned_to
    ON tickets(assigned_to);

CREATE INDEX idx_tickets_category
    ON tickets(category);

CREATE INDEX idx_tickets_priority
    ON tickets(priority);