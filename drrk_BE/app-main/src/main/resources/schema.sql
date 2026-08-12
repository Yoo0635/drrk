CREATE TABLE IF NOT EXISTS inference_message_receipt (
    message_id VARCHAR(36) PRIMARY KEY,
    space_id VARCHAR(255) NOT NULL,
    window_ended_at DOUBLE PRECISION NOT NULL,
    payload TEXT NOT NULL,
    received_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_inference_message_receipt_space_window
    ON inference_message_receipt (space_id, window_ended_at);
