package com.beatgame.websocket.dto;
import java.util.List;
public record RoundStartMessage(int roundNumber, int totalRounds, Long trackId, String previewUrl, List<String> options) {}
