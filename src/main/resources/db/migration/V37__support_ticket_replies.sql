-- V37: add missing replies JSON column to support_tickets.
-- Entity SupportTicket has a JSON `replies` field (conversation history:
-- [{from: "merchant"|"support", text, at}, ...]); V22 omitted the column,
-- so Hibernate schema validation fails with "missing column [replies]".
-- One schema change per ALTER TABLE (TiDB rule).

ALTER TABLE support_tickets
    ADD COLUMN replies JSON NULL COMMENT 'ticket replies: [{from, text, at}]' AFTER customer_email;
