package com.beatgame.websocket;

import com.beatgame.game.GameRedisService;
import com.beatgame.game.GameState;
import com.beatgame.player.Player;
import com.beatgame.player.PlayerRepository;
import com.beatgame.room.Room;
import com.beatgame.room.RoomRepository;
import com.beatgame.room.RoomStatus;
import com.beatgame.websocket.dto.GameOverMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class DisconnectEventListener {

    private static final Logger log = LoggerFactory.getLogger(DisconnectEventListener.class);
    private static final int REJOIN_TIMEOUT_SECONDS = 60;

    private final GameRedisService gameRedisService;
    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ScheduledExecutorService timerExecutor;

    public DisconnectEventListener(GameRedisService gameRedisService,
                                   RoomRepository roomRepository,
                                   PlayerRepository playerRepository,
                                   SimpMessagingTemplate messagingTemplate,
                                   ScheduledExecutorService gameTimerExecutor) {
        this.gameRedisService = gameRedisService;
        this.roomRepository = roomRepository;
        this.playerRepository = playerRepository;
        this.messagingTemplate = messagingTemplate;
        this.timerExecutor = gameTimerExecutor;
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs == null) return;

        String playerToken = (String) attrs.get("playerToken");
        String roomCode = (String) attrs.get("roomCode");
        if (playerToken == null || roomCode == null) return;

        Room room = roomRepository.findByCode(roomCode).orElse(null);
        if (room == null || room.getStatus() != RoomStatus.IN_GAME) return;

        GameState state = gameRedisService.loadGameState(roomCode);
        if (state == null) return;

        if (room.isSoloRoom()) {
            // Solo player has already disconnected — their WebSocket connection is gone,
            // so a GameOverMessage would not be delivered. Just clean up Redis.
            log.info("Solo player {} disconnected from room {} — cleaning up immediately",
                playerToken, roomCode);
            gameRedisService.clearGameData(roomCode);
            return;
        }

        log.info("Player {} disconnected from room {} — starting {}-second rejoin window",
            playerToken, roomCode, REJOIN_TIMEOUT_SECONDS);
        gameRedisService.markDisconnected(roomCode, playerToken);

        timerExecutor.schedule(() -> {
            if (gameRedisService.isDisconnected(roomCode, playerToken)) {
                log.info("Player {} did not rejoin room {} — triggering game over", playerToken, roomCode);
                sendGameOverDueToDisconnect(roomCode, room, playerToken);
            }
        }, REJOIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void sendGameOverDueToDisconnect(String roomCode, Room room, String disconnectedToken) {
        if (room.isSoloRoom()) {
            log.warn("sendGameOverDueToDisconnect called for solo room {} — skipping", roomCode);
            return;
        }
        List<Player> players = playerRepository.findByRoomId(room.getId());
        List<String> tokens = players.stream().map(Player::getPlayerToken).toList();
        Map<String, Integer> scores = gameRedisService.getAllScores(roomCode, tokens);

        String winnerPlayerToken = tokens.stream()
            .filter(t -> !t.equals(disconnectedToken))
            .findFirst()
            .orElse(null);

        if (winnerPlayerToken == null) {
            log.warn("No remaining player found to declare as winner in room {}", roomCode);
        }

        messagingTemplate.convertAndSend("/topic/game." + roomCode,
            new GameOverMessage(scores, winnerPlayerToken));

        gameRedisService.clearGameData(roomCode);
    }
}
