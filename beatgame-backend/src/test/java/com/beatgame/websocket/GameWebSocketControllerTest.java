package com.beatgame.websocket;

import com.beatgame.game.GameService;
import com.beatgame.game.RoundService;
import com.beatgame.player.Player;
import com.beatgame.player.PlayerRepository;
import com.beatgame.room.Room;
import com.beatgame.room.RoomRepository;
import com.beatgame.room.RoomStatus;
import com.beatgame.websocket.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameWebSocketControllerTest {

    @Mock GameService gameService;
    @Mock RoundService roundService;
    @Mock RoomRepository roomRepository;
    @Mock PlayerRepository playerRepository;
    @Mock SimpMessagingTemplate messagingTemplate;

    GameWebSocketController controller;

    @BeforeEach
    void setUp() {
        controller = new GameWebSocketController(gameService, roundService, roomRepository, playerRepository, messagingTemplate);
    }

    private Player playerWithHostFlag(boolean isHost) {
        Player p = new Player();
        p.setHost(isHost);
        return p;
    }

    private SimpMessageHeaderAccessor headerWith(String playerToken, String roomCode) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        accessor.setSessionAttributes(Map.of("playerToken", playerToken, "roomCode", roomCode));
        return accessor;
    }

    private SimpMessageHeaderAccessor headerWithNoRoom() {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        accessor.setSessionAttributes(Map.of());
        return accessor;
    }

    @Test
    void handleStart_usesSessionRoomCode_ignoresBodyRoomCode() {
        StartGameMessage msg = new StartGameMessage("SOMEONE-ELSES-ROOM", 5, "POP", "GENRE");

        controller.handleStart(msg, headerWith("host-tok", "ABC123"));

        verify(gameService).startGame(msg, "host-tok", "ABC123");
    }

    @Test
    void handleAnswer_usesSessionRoomCode_ignoresBodyRoomCode() {
        SubmitAnswerMessage msg = new SubmitAnswerMessage("SOMEONE-ELSES-ROOM", 1L, 0, 5000);

        controller.handleAnswer(msg, headerWith("tok", "ABC123"));

        verify(roundService).submitAnswer(msg, "tok", "ABC123");
    }

    @Test
    void handleReady_usesSessionRoomCode_ignoresBodyRoomCode() {
        ReadyMessage msg = new ReadyMessage("SOMEONE-ELSES-ROOM");
        Room room = new Room();
        room.setMaxPlayers((short) 2);
        room.setStatus(RoomStatus.IN_GAME);
        ReflectionTestUtils.setField(room, "id", 1L);
        when(roomRepository.findByCode("ABC123")).thenReturn(Optional.of(room));

        controller.handleReady(msg, headerWith("tok", "ABC123"));

        verify(roundService).handleReady(msg, "tok", "ABC123", 2);
    }

    @Test
    void handleSubscribed_usesSessionRoomCode_delegatesWithGameInProgressFlag() {
        JoinAckMessage msg = new JoinAckMessage("SOMEONE-ELSES-ROOM");
        Room room = new Room();
        room.setMaxPlayers((short) 2);
        room.setStatus(RoomStatus.IN_GAME);
        ReflectionTestUtils.setField(room, "id", 1L);
        when(roomRepository.findByCode("ABC123")).thenReturn(Optional.of(room));

        controller.handleSubscribed(msg, headerWith("tok", "ABC123"));

        verify(roundService).handleJoinAck("ABC123", "tok", true, 2);
    }

    @Test
    void handleSubscribed_roomStillWaiting_passesGameInProgressFalse() {
        JoinAckMessage msg = new JoinAckMessage("ABC123");
        Room room = new Room();
        room.setMaxPlayers((short) 2);
        room.setStatus(RoomStatus.WAITING);
        ReflectionTestUtils.setField(room, "id", 1L);
        when(roomRepository.findByCode("ABC123")).thenReturn(Optional.of(room));

        controller.handleSubscribed(msg, headerWith("tok", "ABC123"));

        verify(roundService).handleJoinAck("ABC123", "tok", false, 2);
    }

    @Test
    void handleSync_usesSessionRoomCode_ignoresBodyRoomCode() {
        GameSyncMessage msg = new GameSyncMessage("SOMEONE-ELSES-ROOM");

        controller.handleSync(msg, headerWith("tok", "ABC123"));

        verify(gameService).handleSync("ABC123");
    }

    @Test
    void handleConfigUpdate_broadcastsToSessionRoomCode_ignoresBodyRoomCode() {
        RoomConfigMessage msg = new RoomConfigMessage("SOMEONE-ELSES-ROOM", 5, "POP", "GENRE");
        when(playerRepository.findByPlayerToken("host-tok")).thenReturn(Optional.of(playerWithHostFlag(true)));

        controller.handleConfigUpdate(msg, headerWith("host-tok", "ABC123"));

        verify(messagingTemplate).convertAndSend("/topic/room.ABC123", msg);
    }

    @Test
    void handleConfigUpdate_noAuthenticatedRoom_dropsMessage() {
        RoomConfigMessage msg = new RoomConfigMessage("ANY-ROOM", 5, "POP", "GENRE");

        controller.handleConfigUpdate(msg, headerWithNoRoom());

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void handleConfigUpdate_nonHostSender_dropsMessage() {
        RoomConfigMessage msg = new RoomConfigMessage("ABC123", 5, "POP", "GENRE");
        when(playerRepository.findByPlayerToken("guest-tok")).thenReturn(Optional.of(playerWithHostFlag(false)));

        controller.handleConfigUpdate(msg, headerWith("guest-tok", "ABC123"));

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void handleConfigUpdate_unknownPlayerToken_dropsMessage() {
        RoomConfigMessage msg = new RoomConfigMessage("ABC123", 5, "POP", "GENRE");
        when(playerRepository.findByPlayerToken("ghost-tok")).thenReturn(Optional.empty());

        controller.handleConfigUpdate(msg, headerWith("ghost-tok", "ABC123"));

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void handlePause_broadcastsToSessionRoomCode_ignoresBodyRoomCode() {
        GamePauseMessage msg = new GamePauseMessage("SOMEONE-ELSES-ROOM", true);

        controller.handlePause(msg, headerWith("host-tok", "ABC123"));

        verify(messagingTemplate).convertAndSend("/topic/game.ABC123", Map.of("paused", true));
    }

    @Test
    void handlePause_noAuthenticatedRoom_dropsMessage() {
        GamePauseMessage msg = new GamePauseMessage("ANY-ROOM", true);

        controller.handlePause(msg, headerWithNoRoom());

        verifyNoInteractions(messagingTemplate);
    }
}
