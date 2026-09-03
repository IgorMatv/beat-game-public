package com.beatgame.websocket;

import com.beatgame.game.GameRedisService;
import com.beatgame.game.GameService;
import com.beatgame.game.GameState;
import com.beatgame.metrics.GameMetrics;
import com.beatgame.player.Player;
import com.beatgame.player.PlayerRepository;
import com.beatgame.room.Room;
import com.beatgame.room.RoomRepository;
import com.beatgame.room.RoomStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.HashMap;
import java.util.List;
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
    @Mock GameService gameService;
    @Mock GameMetrics gameMetrics;

    DisconnectEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new DisconnectEventListener(gameRedisService, roomRepository,
            playerRepository, messagingTemplate, timerExecutor, gameService, gameMetrics);
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
    void disconnectTimeout_inGameRoomStillDisconnected_marksRoomFinishedAndIncrementsForfeitedMetric() {
        Room room = room("ABC123");
        Player disconnected = player("tok", false);
        Player remaining = player("other-tok", true);
        when(roomRepository.findByCode("ABC123")).thenReturn(Optional.of(room));
        when(gameRedisService.loadGameState("ABC123")).thenReturn(new GameState(3, new Long[]{1L, 2L, 3L}, "POP", "GENRE"));
        when(gameRedisService.isDisconnected("ABC123", "tok")).thenReturn(true);
        when(playerRepository.findByRoomId(any())).thenReturn(List.of(disconnected, remaining));
        when(gameRedisService.getScoresByPlayerId(eq("ABC123"), any())).thenReturn(Map.of("11", 500, "10", 300));

        listener.handleDisconnect(disconnectEvent("tok", "ABC123"));
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(timerExecutor).schedule(captor.capture(), eq(60L), eq(java.util.concurrent.TimeUnit.SECONDS));
        captor.getValue().run();

        org.assertj.core.api.Assertions.assertThat(room.getStatus()).isEqualTo(RoomStatus.FINISHED);
        verify(roomRepository).save(room);
        verify(gameMetrics).incrementGamesCompleted("forfeited");
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

    @Test
    void handleDisconnect_marksDisconnected_whenHostDisconnectsInWaitingRoom() {
        Room room = waitingRoom("ABC123");
        when(roomRepository.findByCode("ABC123")).thenReturn(Optional.of(room));
        when(playerRepository.findByPlayerToken("host-tok")).thenReturn(Optional.of(player("host-tok", true)));

        listener.handleDisconnect(disconnectEvent("host-tok", "ABC123"));

        verify(gameRedisService).markDisconnected("ABC123", "host-tok");
        verify(timerExecutor).schedule(any(Runnable.class), eq(60L), eq(java.util.concurrent.TimeUnit.SECONDS));
    }

    @Test
    void handleDisconnect_doesNothing_whenGuestDisconnectsInWaitingRoom() {
        // Only the host disconnecting is a stuck-state problem — the host can still
        // start the game without the guest present.
        Room room = waitingRoom("ABC123");
        when(roomRepository.findByCode("ABC123")).thenReturn(Optional.of(room));
        when(playerRepository.findByPlayerToken("guest-tok")).thenReturn(Optional.of(player("guest-tok", false)));

        listener.handleDisconnect(disconnectEvent("guest-tok", "ABC123"));

        verify(gameRedisService, never()).markDisconnected(any(), any());
        verify(timerExecutor, never()).schedule(any(Runnable.class), anyLong(), any());
    }

    @Test
    void handleDisconnect_doesNothing_whenSoloRoomWaiting() {
        Room room = waitingRoom("XYZ123");
        room.setMaxPlayers((short) 1);
        when(roomRepository.findByCode("XYZ123")).thenReturn(Optional.of(room));

        listener.handleDisconnect(disconnectEvent("tok", "XYZ123"));

        verify(gameRedisService, never()).markDisconnected(any(), any());
        verifyNoInteractions(playerRepository);
    }

    @Test
    void hostDisconnectTimeout_transfersHostToOtherPlayer_whenStillDisconnected() {
        Room room = waitingRoom("ABC123");
        Player host = player("host-tok", true);
        Player guest = player("guest-tok", false);
        when(roomRepository.findByCode("ABC123")).thenReturn(Optional.of(room));
        when(playerRepository.findByPlayerToken("host-tok")).thenReturn(Optional.of(host));
        when(playerRepository.findByRoomId(any())).thenReturn(List.of(host, guest));
        when(gameRedisService.isDisconnected("ABC123", "host-tok")).thenReturn(true);

        listener.handleDisconnect(disconnectEvent("host-tok", "ABC123"));
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(timerExecutor).schedule(captor.capture(), eq(60L), eq(java.util.concurrent.TimeUnit.SECONDS));
        captor.getValue().run();

        org.assertj.core.api.Assertions.assertThat(host.isHost()).isFalse();
        org.assertj.core.api.Assertions.assertThat(guest.isHost()).isTrue();
        verify(playerRepository).save(host);
        verify(playerRepository).save(guest);
        verify(gameService).broadcastRoomState("ABC123");
    }

    @Test
    void hostDisconnectTimeout_doesNothing_whenHostReconnectedInTime() {
        Room room = waitingRoom("ABC123");
        when(roomRepository.findByCode("ABC123")).thenReturn(Optional.of(room));
        when(playerRepository.findByPlayerToken("host-tok")).thenReturn(Optional.of(player("host-tok", true)));
        when(gameRedisService.isDisconnected("ABC123", "host-tok")).thenReturn(false);

        listener.handleDisconnect(disconnectEvent("host-tok", "ABC123"));
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(timerExecutor).schedule(captor.capture(), eq(60L), eq(java.util.concurrent.TimeUnit.SECONDS));
        captor.getValue().run();

        verify(playerRepository, never()).save(any());
        verifyNoInteractions(gameService);
    }

    @Test
    void hostDisconnectTimeout_doesNothing_whenNoOtherPlayerInRoom() {
        Room room = waitingRoom("ABC123");
        Player host = player("host-tok", true);
        when(roomRepository.findByCode("ABC123")).thenReturn(Optional.of(room));
        when(playerRepository.findByPlayerToken("host-tok")).thenReturn(Optional.of(host));
        when(playerRepository.findByRoomId(any())).thenReturn(List.of(host));
        when(gameRedisService.isDisconnected("ABC123", "host-tok")).thenReturn(true);

        listener.handleDisconnect(disconnectEvent("host-tok", "ABC123"));
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(timerExecutor).schedule(captor.capture(), eq(60L), eq(java.util.concurrent.TimeUnit.SECONDS));
        captor.getValue().run();

        verify(playerRepository, never()).save(any());
        verifyNoInteractions(gameService);
    }

    @Test
    void hostDisconnectTimeout_doesNothing_whenRoomNoLongerWaiting() {
        // Covers the room having started (or been deleted/reset) in the time between
        // the disconnect and the 60s timeout firing.
        Room stillWaitingAtDisconnect = waitingRoom("ABC123");
        Room inGameByTimeout = waitingRoom("ABC123");
        inGameByTimeout.setStatus(RoomStatus.IN_GAME);
        when(roomRepository.findByCode("ABC123"))
            .thenReturn(Optional.of(stillWaitingAtDisconnect))
            .thenReturn(Optional.of(inGameByTimeout));
        when(playerRepository.findByPlayerToken("host-tok")).thenReturn(Optional.of(player("host-tok", true)));
        when(gameRedisService.isDisconnected("ABC123", "host-tok")).thenReturn(true);

        listener.handleDisconnect(disconnectEvent("host-tok", "ABC123"));
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(timerExecutor).schedule(captor.capture(), eq(60L), eq(java.util.concurrent.TimeUnit.SECONDS));
        captor.getValue().run();

        verify(playerRepository, never()).save(any());
        verifyNoInteractions(gameService);
    }

    private Room waitingRoom(String code) {
        Room room = new Room();
        room.setCode(code);
        room.setStatus(RoomStatus.WAITING);
        room.setMaxPlayers((short) 2);
        ReflectionTestUtils.setField(room, "id", 3L);
        return room;
    }

    private Player player(String token, boolean isHost) {
        Player player = new Player();
        player.setPlayerToken(token);
        player.setHost(isHost);
        ReflectionTestUtils.setField(player, "id", isHost ? 10L : 11L);
        return player;
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
