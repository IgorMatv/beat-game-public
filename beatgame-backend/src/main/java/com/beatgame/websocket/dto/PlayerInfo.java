package com.beatgame.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PlayerInfo(Long id, String name, @JsonProperty("isHost") boolean host) {}
