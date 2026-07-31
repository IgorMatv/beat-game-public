package com.beatgame.websocket;

import com.beatgame.game.GameService;
import com.beatgame.game.RoundService;
import com.beatgame.player.Player;
import com.beatgame.player.PlayerRepository;
import com.beatgame.room.Room;
import com.beatgame.room.RoomRepository;
import com.beatgame.room.RoomStatus;
import com.beatgame.websocket.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class GameWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketController.class);

    private final GameService gameService;
    private final RoundService roundService;
    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public GameWebSocketController(GameService gameService,
                                   RoundService roundService,
                                   RoomRepository roomRepository,
                                   PlayerRepository playerRepository,
                                   SimpMessagingTemplate messagingTemplate) {
        this.gameService = gameService;
        this.roundService = roundService;
        this.roomRepository = roomRepository;
        this.playerRepository = playerRepository;
        this.messagingTemplate = messagingTemplate;
    }

    private String extractPlayerToken(SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> attrs = headerAccessor.getSessionAttributes();
        if (attrs == null) return null;
        return (String) attrs.get("playerToken");
    }

    private String extractRoomCode(SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> attrs = headerAccessor.getSessionAttributes();
        if (attrs == null) return null;
        return (String) attrs.get("roomCode");
    }

    @MessageMapping("game.start")
    public void handleStart(@Payload StartGameMessage msg, SimpMessageHeaderAccessor headerAccessor) {
        String playerToken = extractPlayerToken(headerAccessor);
        String roomCode = extractRoomCode(headerAccessor);
        gameService.startGame(msg, playerToken, roomCode);
    }

    @MessageMapping("game.answer")
    public void handleAnswer(@Payload SubmitAnswerMessage msg, SimpMessageHeaderAccessor headerAccessor) {
        String playerToken = extractPlayerToken(headerAccessor);
        String roomCode = extractRoomCode(headerAccessor);
        roundService.submitAnswer(msg, playerToken, roomCode);
    }

    @MessageMapping("game.ready")
    public void handleReady(@Payload ReadyMessage msg, SimpMessageHeaderAccessor headerAccessor) {
        String playerToken = extractPlayerToken(headerAccessor);
        String roomCode = extractRoomCode(headerAccessor);
        Room room = roomRepository.findByCode(roomCode)
            .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomCode));
        roundService.handleReady(msg, playerToken, roomCode, room.getMaxPlayers());
    }

    @MessageMapping("game.subscribed")
    public void handleSubscribed(@Payload JoinAckMessage msg, SimpMessageHeaderAccessor headerAccessor) {
        String playerToken = extractPlayerToken(headerAccessor);
        String roomCode = extractRoomCode(headerAccessor);
        Room room = roomRepository.findByCode(roomCode)
            .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomCode));
        roundService.handleJoinAck(roomCode, playerToken, room.getStatus() == RoomStatus.IN_GAME, room.getMaxPlayers());
    }

    @MessageMapping("game.sync")
    public void handleSync(@Payload GameSyncMessage msg, SimpMessageHeaderAccessor headerAccessor) {
        String roomCode = extractRoomCode(headerAccessor);
        gameService.handleSync(roomCode);
    }

    @MessageMapping("room.config")
    public void handleConfigUpdate(@Payload RoomConfigMessage msg, SimpMessageHeaderAccessor headerAccessor) {
        String roomCode = extractRoomCode(headerAccessor);
        if (roomCode == null) {
            log.warn("Dropping room.config: no authenticated room on this session");
            return;
        }
        String playerToken = extractPlayerToken(headerAccessor);
        boolean isHost = playerRepository.findByPlayerToken(playerToken)
            .map(Player::isHost)
            .orElse(false);
        if (!isHost) {
            log.warn("Dropping room.config: sender is not the host of room {}", roomCode);
            return;
        }
        messagingTemplate.convertAndSend("/topic/room." + roomCode, msg);
    }

    @MessageMapping("game.pause")
    public void handlePause(@Payload GamePauseMessage msg, SimpMessageHeaderAccessor headerAccessor) {
        String roomCode = extractRoomCode(headerAccessor);
        if (roomCode == null) {
            log.warn("Dropping game.pause: no authenticated room on this session");
            return;
        }
        messagingTemplate.convertAndSend("/topic/game." + roomCode, Map.of("paused", msg.paused()));
    }
}
