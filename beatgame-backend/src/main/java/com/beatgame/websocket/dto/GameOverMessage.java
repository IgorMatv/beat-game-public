package com.beatgame.websocket.dto;
import java.util.Map;
public record GameOverMessage(Map<String, Integer> scores, String winnerPlayerId) {}
