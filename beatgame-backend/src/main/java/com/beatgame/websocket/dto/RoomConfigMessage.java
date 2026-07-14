package com.beatgame.websocket.dto;
public record RoomConfigMessage(String roomCode, int rounds, String category, String categoryType) {}
