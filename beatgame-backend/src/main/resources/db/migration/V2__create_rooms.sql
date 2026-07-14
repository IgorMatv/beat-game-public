CREATE TABLE rooms (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(6)  UNIQUE NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    max_players SMALLINT    NOT NULL DEFAULT 2,
    config      JSONB,
    created_at  TIMESTAMP   DEFAULT NOW()
);

CREATE INDEX idx_rooms_code ON rooms (code);
CREATE INDEX idx_rooms_status ON rooms (status);
