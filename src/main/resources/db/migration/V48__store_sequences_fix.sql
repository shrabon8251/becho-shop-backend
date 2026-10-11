-- Fix BUG-50: ensure store_sequences table exists for order number generation
CREATE TABLE IF NOT EXISTS store_sequences (
                                               store_id BIGINT NOT NULL,
                                               seq_name VARCHAR(64) NOT NULL,
    next_val BIGINT NOT NULL DEFAULT 1,
    PRIMARY KEY (store_id, seq_name)
    );
