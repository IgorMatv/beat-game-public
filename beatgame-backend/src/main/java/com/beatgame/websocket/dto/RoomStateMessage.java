package com.beatgame.websocket.dto;
import java.util.List;
public record RoomStateMessage(List<PlayerInfo> players, String status) {}
