package com.beatgame.websocket;

import com.beatgame.game.GameService;
import com.beatgame.game.RoundService;
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
    @Mock SimpMessagingTemplate messagingTemplate;

    GameWebSocketController controller;

    @BeforeEach
    void setUp() {
        controller = new GameWebSocketController(gameService, roundService, roomRepository, messagingTemplate);
    }

    private SimpMessageHeaderAccessor headerWith(String playerToken) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        accessor.setSessionAttributes(Map.of("playerToken", playerToken));
        return accessor;
    }

    @Test
    void handleStart_delegatesToGameService() {
        StartGameMessage msg = new StartGameMessage("ABC123", 5, "POP", "GENRE");

        controller.handleStart(msg, headerWith("host-tok"));

        verify(gameService).startGame(msg, "host-tok");
    }

    @Test
    void handleAnswer_delegatesToRoundService() {
        SubmitAnswerMessage msg = new SubmitAnswerMessage("ABC123", 1L, 0, 5000);

        controller.handleAnswer(msg, headerWith("tok"));

        verify(roundService).submitAnswer(msg, "tok");
    }

    @Test
    void handleReady_delegatesToRoundService() {
        ReadyMessage msg = new ReadyMessage("ABC123");
        Room room = new Room();
        room.setMaxPlayers((short) 2);
        room.setStatus(RoomStatus.IN_GAME);
        ReflectionTestUtils.setField(room, "id", 1L);
        when(roomRepository.findByCode("ABC123")).thenReturn(Optional.of(room));

        controller.handleReady(msg, headerWith("tok"));

        verify(roundService).handleReady(msg, "tok", 2);
    }

    @Test
    void handleRejoin_delegatesToGameService() {
        RejoinMessage msg = new RejoinMessage("ABC123", "tok");

        controller.handleRejoin(msg);

        verify(gameService).handleRejoin(msg);
    }
}
