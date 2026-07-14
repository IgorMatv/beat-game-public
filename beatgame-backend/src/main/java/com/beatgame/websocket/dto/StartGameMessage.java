package com.beatgame.websocket.dto;
public record StartGameMessage(String roomCode, int rounds, String category, String categoryType) {}
