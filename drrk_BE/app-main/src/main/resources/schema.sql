CREATE TABLE IF NOT EXISTS inference_message_receipt (
    message_id VARCHAR(36) PRIMARY KEY,
    space_id VARCHAR(255) NOT NULL,
    window_ended_at DOUBLE PRECISION NOT NULL,
    payload TEXT NOT NULL,
    received_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_inference_message_receipt_space_window
    ON inference_message_receipt (space_id, window_ended_at);

CREATE TABLE IF NOT EXISTS congestion_history (
    message_id VARCHAR(36) PRIMARY KEY,
    calculated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    received_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    status VARCHAR(64) NOT NULL,
    calculation_version VARCHAR(255) NOT NULL,
    score DOUBLE PRECISION,
    current_load DOUBLE PRECISION,
    forecast_load DOUBLE PRECISION,
    level VARCHAR(64),
    capacity BIGINT,
    payload TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_congestion_history_calculated_at
    ON congestion_history (calculated_at);
