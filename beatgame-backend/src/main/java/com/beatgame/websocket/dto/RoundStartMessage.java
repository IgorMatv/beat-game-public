package com.beatgame.websocket.dto;
import java.util.List;
import java.util.Map;
public record RoundStartMessage(int roundNumber, int totalRounds, Long trackId, String previewUrl, List<String> options, int remainingSeconds, Map<String, Integer> scores) {}
