package com.beatgame.websocket;

import com.beatgame.game.GameService;
import com.beatgame.game.RoundService;
import com.beatgame.room.Room;
import com.beatgame.room.RoomRepository;
import com.beatgame.websocket.dto.*;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class GameWebSocketController {

    private final GameService gameService;
    private final RoundService roundService;
    private final RoomRepository roomRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public GameWebSocketController(GameService gameService,
                                   RoundService roundService,
                                   RoomRepository roomRepository,
                                   SimpMessagingTemplate messagingTemplate) {
        this.gameService = gameService;
        this.roundService = roundService;
        this.roomRepository = roomRepository;
        this.messagingTemplate = messagingTemplate;
    }

    private String extractPlayerToken(SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> attrs = headerAccessor.getSessionAttributes();
        if (attrs == null) return null;
        return (String) attrs.get("playerToken");
    }

    @MessageMapping("game.start")
    public void handleStart(@Payload StartGameMessage msg, SimpMessageHeaderAccessor headerAccessor) {
        String playerToken = extractPlayerToken(headerAccessor);
        gameService.startGame(msg, playerToken);
    }

    @MessageMapping("game.answer")
    public void handleAnswer(@Payload SubmitAnswerMessage msg, SimpMessageHeaderAccessor headerAccessor) {
        String playerToken = extractPlayerToken(headerAccessor);
        roundService.submitAnswer(msg, playerToken);
    }

    @MessageMapping("game.ready")
    public void handleReady(@Payload ReadyMessage msg, SimpMessageHeaderAccessor headerAccessor) {
        String playerToken = extractPlayerToken(headerAccessor);
        Room room = roomRepository.findByCode(msg.roomCode())
            .orElseThrow(() -> new IllegalArgumentException("Room not found: " + msg.roomCode()));
        roundService.handleReady(msg, playerToken, room.getMaxPlayers());
    }

    @MessageMapping("game.rejoin")
    public void handleRejoin(@Payload RejoinMessage msg) {
        gameService.handleRejoin(msg);
    }

    @MessageMapping("room.config")
    public void handleConfigUpdate(@Payload RoomConfigMessage msg) {
        messagingTemplate.convertAndSend("/topic/room." + msg.roomCode(), msg);
    }

    @MessageMapping("game.pause")
    public void handlePause(@Payload GamePauseMessage msg) {
        messagingTemplate.convertAndSend("/topic/game." + msg.roomCode(),
            java.util.Map.of("paused", msg.paused()));
    }
}
