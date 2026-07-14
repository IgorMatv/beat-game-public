package com.beatgame.track.admin;

public record PopulationResult(
        String provider,
        String categoryType,
        String category,
        int pages,
        int startOffset,
        int fetched,
        int saved,
        int skipped
) {}
