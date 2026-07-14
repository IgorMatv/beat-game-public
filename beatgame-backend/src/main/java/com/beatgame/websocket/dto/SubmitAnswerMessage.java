package com.beatgame.websocket.dto;
public record SubmitAnswerMessage(String roomCode, long trackId, int answerIndex, int timeMs) {}
