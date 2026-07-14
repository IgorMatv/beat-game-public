package com.beatgame.websocket;

import com.beatgame.game.GameRedisService;
import com.beatgame.game.GameState;
import com.beatgame.player.PlayerRepository;
import com.beatgame.room.Room;
import com.beatgame.room.RoomRepository;
import com.beatgame.room.RoomStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisconnectEventListenerTest {

    @Mock GameRedisService gameRedisService;
    @Mock RoomRepository roomRepository;
    @Mock PlayerRepository playerRepository;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock ScheduledExecutorService timerExecutor;

    DisconnectEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new DisconnectEventListener(gameRedisService, roomRepository,
            playerRepository, messagingTemplate, timerExecutor);
    }

    @Test
    void handleDisconnect_marksDisconnectedInRedis_whenPlayerIsInGame() {
        GameState state = new GameState(3, new Long[]{1L, 2L, 3L}, "POP", "GENRE");
        Room room = room("ABC123");
        when(roomRepository.findByCode("ABC123")).thenReturn(Optional.of(room));
        when(gameRedisService.loadGameState("ABC123")).thenReturn(state);

        listener.handleDisconnect(disconnectEvent("tok", "ABC123"));

        verify(gameRedisService).markDisconnected("ABC123", "tok");
    }

    @Test
    void handleDisconnect_doesNothing_whenRoomNotInActiveGame() {
        Room room = room("ABC123");
        room.setStatus(RoomStatus.WAITING);
        when(roomRepository.findByCode("ABC123")).thenReturn(Optional.of(room));

        listener.handleDisconnect(disconnectEvent("tok", "ABC123"));

        verify(gameRedisService, never()).markDisconnected(any(), any());
    }

    @Test
    void handleDisconnect_doesNothing_whenPlayerTokenAbsent() {
        listener.handleDisconnect(disconnectEvent(null, null));

        verifyNoInteractions(gameRedisService);
        verifyNoInteractions(roomRepository);
    }

    @Test
    void handleDisconnect_clearsGameDataImmediately_whenSoloRoom() {
        GameState state = new GameState(1, new Long[]{1L}, "POP", "GENRE");
        Room room = soloRoom("XYZ123");
        when(roomRepository.findByCode("XYZ123")).thenReturn(Optional.of(room));
        when(gameRedisService.loadGameState("XYZ123")).thenReturn(state);

        listener.handleDisconnect(disconnectEvent("tok", "XYZ123"));

        verify(gameRedisService).clearGameData("XYZ123");
        verify(gameRedisService, never()).markDisconnected(any(), any());
        verify(timerExecutor, never()).schedule(any(Runnable.class), anyLong(), any());
    }

    private SessionDisconnectEvent disconnectEvent(String playerToken, String roomCode) {
        Map<String, Object> sessionAttrs = new HashMap<>();
        if (playerToken != null) sessionAttrs.put("playerToken", playerToken);
        if (roomCode != null) sessionAttrs.put("roomCode", roomCode);

        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create();
        headerAccessor.setSessionAttributes(sessionAttrs);

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], headerAccessor.toMessageHeaders());

        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        when(event.getMessage()).thenReturn(message);
        return event;
    }

    private Room room(String code) {
        Room room = new Room();
        room.setCode(code);
        room.setStatus(RoomStatus.IN_GAME);
        room.setMaxPlayers((short) 2);
        ReflectionTestUtils.setField(room, "id", 1L);
        return room;
    }

    private Room soloRoom(String code) {
        Room room = new Room();
        room.setCode(code);
        room.setStatus(RoomStatus.IN_GAME);
        room.setMaxPlayers((short) 1);
        ReflectionTestUtils.setField(room, "id", 2L);
        return room;
    }
}
