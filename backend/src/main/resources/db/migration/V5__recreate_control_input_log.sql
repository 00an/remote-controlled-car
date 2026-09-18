CREATE TABLE IF NOT EXISTS control_input_log (
                                                 id BIGSERIAL NOT NULL,
                                                 recorded_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    client_timestamp BIGINT NOT NULL,
    steering DOUBLE PRECISION NOT NULL,
    throttle DOUBLE PRECISION NOT NULL,
    brake DOUBLE PRECISION NOT NULL,
    buttons TEXT,
    hats TEXT,
    source VARCHAR(16) NOT NULL DEFAULT 'unknown',
    session_id UUID,
    CONSTRAINT control_input_log_pkey PRIMARY KEY (id)
    );
CREATE INDEX IF NOT EXISTS idx_control_input_log_recorded_at ON control_input_log (recorded_at);
CREATE INDEX IF NOT EXISTS idx_control_input_log_session_id ON control_input_log (session_id);