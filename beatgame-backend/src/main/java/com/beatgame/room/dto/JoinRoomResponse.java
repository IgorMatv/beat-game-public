package com.beatgame.room.dto;

import com.beatgame.websocket.dto.PlayerInfo;
import java.util.List;

public record JoinRoomResponse(String playerToken, Long playerId, List<PlayerInfo> players) {}
