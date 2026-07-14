CREATE UNIQUE INDEX IF NOT EXISTS idx_tracks_provider_external_id
    ON tracks (provider, external_id);
