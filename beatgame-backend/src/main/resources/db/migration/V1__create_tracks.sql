CREATE TABLE tracks (
    id           BIGSERIAL PRIMARY KEY,
    title        VARCHAR(255) NOT NULL,
    artist       VARCHAR(255) NOT NULL,
    genre        VARCHAR(100),
    decade       SMALLINT,
    preview_url  VARCHAR(512) NOT NULL,
    cover_url    VARCHAR(512),
    provider     VARCHAR(50),
    external_id  VARCHAR(100),
    play_count   INTEGER      DEFAULT 0,
    last_used_at TIMESTAMP,
    archived     BOOLEAN      DEFAULT FALSE,
    added_at     TIMESTAMP    DEFAULT NOW()
);

CREATE INDEX idx_tracks_genre_archived ON tracks (genre, archived);
CREATE INDEX idx_tracks_decade_archived ON tracks (decade, archived);
CREATE INDEX idx_tracks_archived ON tracks (archived);
