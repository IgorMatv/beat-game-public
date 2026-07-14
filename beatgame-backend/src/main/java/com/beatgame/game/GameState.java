package com.beatgame.game;

public record GameState(int totalRounds, Long[] trackIds, String category, String categoryType) {}
