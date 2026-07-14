package com.beatgame.game;

import com.beatgame.player.Player;
import com.beatgame.player.PlayerRepository;
import com.beatgame.room.Room;
import com.beatgame.room.RoomRepository;
import com.beatgame.room.RoomStatus;
import com.beatgame.track.Track;
import com.beatgame.track.TrackRepository;
import com.beatgame.websocket.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RoundServiceTest {

    @Mock GameRedisService gameRedisService;
    @Mock GameSessionRepository gameSessionRepository;
    @Mock RoomRepository roomRepository;
    @Mock PlayerRepository playerRepository;
    @Mock TrackRepository trackRepository;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock ScheduledExecutorService timerExecutor;
    @Mock GameService gameService;
    @Mock PlatformTransactionManager transactionManager;

    RoundService roundService;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        lenient().when(timerExecutor.schedule(any(Runnable.class), anyLong(), any())).thenReturn(mock(java.util.concurrent.ScheduledFuture.class));
        roundService = new RoundService(gameRedisService, gameSessionRepository, roomRepository,
            playerRepository, trackRepository, messagingTemplate, timerExecutor, gameService, transactionManager);
    }

    @Test
    void calculateScore_correctAnswerAtStart_returns1000() {
        assertThat(roundService.calculateScore(true, 0)).isEqualTo(1000);
    }

    @Test
    void calculateScore_correctAnswerAfter5s_returns666() {
        // 1000 * (15000 - 5000) / 15000 = 666 (truncated)
        assertThat(roundService.calculateScore(true, 5_000)).isEqualTo(666);
    }

    @Test
    void calculateScore_correctAnswerAfter10s_returns333() {
        // 1000 * (15000 - 10000) / 15000 = 333 (truncated)
        assertThat(roundService.calculateScore(true, 10_000)).isEqualTo(333);
    }

    @Test
    void calculateScore_correctAnswerAtRoundEnd_clampsTo100() {
        // raw: 1000 * (15000 - 15000) / 15000 = 0, floor applies → 100
        assertThat(roundService.calculateScore(true, 15_000)).isEqualTo(100);
    }

    @Test
    void calculateScore_correctAnswerBeyondRoundTime_clampsTo100() {
        // raw: 1000 * (15000 - 20000) / 15000 = -333, floor applies → 100
        assertThat(roundService.calculateScore(true, 20_000)).isEqualTo(100);
    }

    @Test
    void calculateScore_correctAnswerWhenRawScoreBelowFloor_clampsTo100() {
        // raw: 1000 * (15000 - 14000) / 15000 = 66, floor applies → 100
        assertThat(roundService.calculateScore(true, 14_000)).isEqualTo(100);
    }

    @Test
    void calculateScore_wrongAnswer_returns0() {
        assertThat(roundService.calculateScore(false, 1000)).isEqualTo(0);
    }

    @Test
    void submitAnswer_ignoresDuplicateAnswer() {
        when(gameRedisService.markAnswered(any(), anyInt(), any())).thenReturn(false);

        roundService.submitAnswer(new SubmitAnswerMessage("ABC123", 1L, 0, 3000), "tok");

        verify(gameRedisService, never()).incrementAnswers(any(), anyInt());
    }

    @Test
    void submitAnswer_addsScoreForCorrectAnswer() {
        Room room = roomWithCode("ABC123", 2);
        GameState state = new GameState(3, new Long[]{1L, 2L, 3L}, "POP", "GENRE");
        when(gameRedisService.markAnswered(any(), anyInt(), any())).thenReturn(true);
        when(gameRedisService.getCurrentRound("ABC123")).thenReturn(1);
        when(gameRedisService.loadGameState("ABC123")).thenReturn(state);
        when(roomRepository.findByCode("ABC123")).thenReturn(Optional.of(room));
        when(gameRedisService.incrementAnswers("ABC123", 1)).thenReturn(1L);
        when(gameRedisService.getCorrectIndex("ABC123", 1)).thenReturn(2);

        // Correct: trackId matches the round's track, answerIndex matches stored correct index
        roundService.submitAnswer(new SubmitAnswerMessage("ABC123", 1L, 2, 5000), "tok");

        verify(gameRedisService).addScore(eq("ABC123"), eq("tok"), eq(666)); // 1000 * 10000/15000 = 666
    }

    @Test
    void submitAnswer_addsZeroScoreForWrongAnswer() {
        Room room = roomWithCode("ABC123", 2);
        GameState state = new GameState(3, new Long[]{1L, 2L, 3L}, "POP", "GENRE");
        when(gameRedisService.markAnswered(any(), anyInt(), any())).thenReturn(true);
        when(gameRedisService.getCurrentRound("ABC123")).thenReturn(1);
        when(gameRedisService.loadGameState("ABC123")).thenReturn(state);
        when(roomRepository.findByCode("ABC123")).thenReturn(Optional.of(room));
        when(gameRedisService.incrementAnswers("ABC123", 1)).thenReturn(1L);
        when(gameRedisService.getCorrectIndex("ABC123", 1)).thenReturn(2);

        // Wrong: trackId matches (not stale) but answerIndex=0 != correctIndex=2
        roundService.submitAnswer(new SubmitAnswerMessage("ABC123", 1L, 0, 3000), "tok");

        verify(gameRedisService).addScore(eq("ABC123"), eq("tok"), eq(0));
    }

    @Test
    void submitAnswer_ignoresStaleRoundAnswer() {
        GameState state = new GameState(3, new Long[]{1L, 2L, 3L}, "POP", "GENRE");
        when(gameRedisService.markAnswered(any(), anyInt(), any())).thenReturn(true);
        when(gameRedisService.getCurrentRound("ABC123")).thenReturn(1);
        when(gameRedisService.loadGameState("ABC123")).thenReturn(state);

        // Stale: trackId=99 doesn't match round's trackId=1
        roundService.submitAnswer(new SubmitAnswerMessage("ABC123", 99L, 0, 3000), "tok");

        verify(gameRedisService, never()).addScore(any(), any(), anyInt());
    }

    @Test
    void submitAnswer_closesRoundWhenAllAnswered() {
        Room room = roomWithCode("ABC123", 1);
        GameState state = new GameState(1, new Long[]{1L}, "POP", "GENRE");
        Player player = new Player();
        player.setPlayerToken("tok");
        Track track = new Track();
        track.setTitle("T");
        track.setArtist("A");
        ReflectionTestUtils.setField(track, "id", 1L);

        when(gameRedisService.markAnswered(any(), anyInt(), any())).thenReturn(true);
        when(gameRedisService.getCurrentRound("ABC123")).thenReturn(1);
        when(gameRedisService.loadGameState("ABC123")).thenReturn(state);
        when(roomRepository.findByCode("ABC123")).thenReturn(Optional.of(room));
        when(gameRedisService.incrementAnswers("ABC123", 1)).thenReturn(1L); // 1 == maxPlayers(1)
        // closeRound will be called — mock claimRoundClose to allow entry
        when(gameRedisService.claimRoundClose("ABC123", 1)).thenReturn(true);
        when(playerRepository.findByRoomId(any())).thenReturn(List.of(player));
        when(trackRepository.findById(1L)).thenReturn(Optional.of(track));
        when(gameRedisService.getAllScores(any(), any())).thenReturn(Map.of("tok", 750));
        when(gameSessionRepository.findTopByRoomIdOrderByStartedAtDesc(any())).thenReturn(Optional.empty());

        roundService.submitAnswer(new SubmitAnswerMessage("ABC123", 1L, 0, 5000), "tok");

        // Verify RoundResultMessage sent immediately; GameOverMessage is scheduled (4-second delay)
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/game.ABC123"), any(RoundResultMessage.class));
        verify(timerExecutor).schedule(any(Runnable.class), eq(4L), eq(java.util.concurrent.TimeUnit.SECONDS));
    }

    private Room roomWithCode(String code, int maxPlayers) {
        Room room = new Room();
        room.setCode(code);
        room.setMaxPlayers((short) maxPlayers);
        room.setStatus(com.beatgame.room.RoomStatus.IN_GAME);
        ReflectionTestUtils.setField(room, "id", 1L);
        return room;
    }
}
