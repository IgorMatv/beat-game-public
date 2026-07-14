CREATE TABLE players (
    id           BIGSERIAL PRIMARY KEY,
    room_id      BIGINT      NOT NULL REFERENCES rooms (id) ON DELETE CASCADE,
    name         VARCHAR(50) NOT NULL,
    score        INTEGER     DEFAULT 0,
    is_host      BOOLEAN     DEFAULT FALSE,
    player_token VARCHAR(64),
    joined_at    TIMESTAMP   DEFAULT NOW()
);

CREATE INDEX idx_players_room_id ON players (room_id);
CREATE INDEX idx_players_token ON players (player_token);
