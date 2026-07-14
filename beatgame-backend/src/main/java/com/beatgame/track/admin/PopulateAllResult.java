package com.beatgame.track.admin;

import java.util.List;

public record PopulateAllResult(
        int totalFetched,
        int totalSaved,
        int totalSkipped,
        List<PopulationResult> byCategory
) {}
