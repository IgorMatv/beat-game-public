-- Backfill any null player_token values before adding NOT NULL constraint
UPDATE players
SET player_token = gen_random_uuid()::text
WHERE player_token IS NULL;

ALTER TABLE players
    ALTER COLUMN player_token SET NOT NULL;

DROP INDEX IF EXISTS idx_players_token;

CREATE UNIQUE INDEX IF NOT EXISTS idx_players_player_token
    ON players (player_token);
