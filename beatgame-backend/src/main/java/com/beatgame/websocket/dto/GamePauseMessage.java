package com.beatgame.websocket.dto;
public record GamePauseMessage(String roomCode, boolean paused) {}
