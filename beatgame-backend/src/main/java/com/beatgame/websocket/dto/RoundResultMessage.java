package com.beatgame.websocket.dto;
import java.util.Map;
public record RoundResultMessage(int roundNumber, Long correctTrackId, String correctAnswer, Map<String, Integer> scores) {}
