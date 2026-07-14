CREATE TABLE game_sessions (
    id            BIGSERIAL PRIMARY KEY,
    room_id       BIGINT    NOT NULL REFERENCES rooms (id) ON DELETE CASCADE,
    track_ids     BIGINT[]  NOT NULL DEFAULT '{}',
    current_round SMALLINT  DEFAULT 0,
    started_at    TIMESTAMP DEFAULT NOW(),
    finished_at   TIMESTAMP
);

CREATE INDEX idx_game_sessions_room_id ON game_sessions (room_id);
