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
import org.mockito.ArgumentCaptor;
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
import java.util.concurrent.TimeUnit;

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
        ReflectionTestUtils.setField(player, "id", 5L);
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
        when(gameRedisService.getScoresByPlayerId(any(), any())).thenReturn(Map.of("5", 750));
        when(gameSessionRepository.findTopByRoomIdOrderByStartedAtDesc(any())).thenReturn(Optional.empty());

        roundService.submitAnswer(new SubmitAnswerMessage("ABC123", 1L, 0, 5000), "tok");

        // Verify RoundResultMessage sent immediately; GameOverMessage is scheduled (4-second delay)
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/game.ABC123"), any(RoundResultMessage.class));
        verify(timerExecutor).schedule(any(Runnable.class), eq(4L), eq(java.util.concurrent.TimeUnit.SECONDS));
    }

    @Test
    void submitAnswer_closesRoundAndEndsGame_broadcastsNeverContainRawPlayerToken() {
        // RoundResultMessage/GameOverMessage broadcast to every player in the room over
        // the topic — must be keyed by playerId, never the raw playerToken, or any
        // player could read another's token straight out of the payload (issue #24).
        Room room = roomWithCode("ABC123", 1);
        GameState state = new GameState(1, new Long[]{1L}, "POP", "GENRE");
        Player player = new Player();
        player.setPlayerToken("secret-tok");
        ReflectionTestUtils.setField(player, "id", 5L);
        Track track = new Track();
        track.setTitle("T");
        track.setArtist("A");
        ReflectionTestUtils.setField(track, "id", 1L);

        when(gameRedisService.markAnswered(any(), anyInt(), any())).thenReturn(true);
        when(gameRedisService.getCurrentRound("ABC123")).thenReturn(1);
        when(gameRedisService.loadGameState("ABC123")).thenReturn(state);
        when(roomRepository.findByCode("ABC123")).thenReturn(Optional.of(room));
        when(gameRedisService.incrementAnswers("ABC123", 1)).thenReturn(1L);
        when(gameRedisService.claimRoundClose("ABC123", 1)).thenReturn(true);
        when(playerRepository.findByRoomId(any())).thenReturn(List.of(player));
        when(trackRepository.findById(1L)).thenReturn(Optional.of(track));
        when(gameRedisService.getScoresByPlayerId(any(), any())).thenReturn(Map.of("5", 750));
        when(gameSessionRepository.findTopByRoomIdOrderByStartedAtDesc(any())).thenReturn(Optional.empty());

        ArgumentCaptor<Runnable> gameOverRunnable = ArgumentCaptor.forClass(Runnable.class);
        when(timerExecutor.schedule(gameOverRunnable.capture(), eq(4L), eq(java.util.concurrent.TimeUnit.SECONDS)))
            .thenReturn(mock(java.util.concurrent.ScheduledFuture.class));

        roundService.submitAnswer(new SubmitAnswerMessage("ABC123", 1L, 0, 5000), "secret-tok");
        gameOverRunnable.getValue().run(); // fire the scheduled GameOverMessage broadcast

        ArgumentCaptor<Object> payloads = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, times(2)).convertAndSend(eq("/topic/game.ABC123"), payloads.capture());

        RoundResultMessage resultMsg = payloads.getAllValues().stream()
            .filter(RoundResultMessage.class::isInstance).map(RoundResultMessage.class::cast).findFirst().orElseThrow();
        assertThat(resultMsg.scores()).containsExactly(Map.entry("5", 750));
        assertThat(resultMsg.scores()).doesNotContainKey("secret-tok");

        GameOverMessage overMsg = payloads.getAllValues().stream()
            .filter(GameOverMessage.class::isInstance).map(GameOverMessage.class::cast).findFirst().orElseThrow();
        assertThat(overMsg.scores()).doesNotContainKey("secret-tok");
        assertThat(overMsg.winnerPlayerId()).isEqualTo("5");
    }

    @Test
    void armRound1_soloRoom_startsImmediatelyWithoutCheckingJoinAcks() {
        when(gameRedisService.claimRoundStart("ABC123", 1)).thenReturn(true);
        RoundStartMessage msg = new RoundStartMessage(1, 3, 1L, "url", List.of("a", "b", "c", "d"), 15, Map.of());
        when(gameService.buildRoundStartForRound("ABC123", 1)).thenReturn(msg);

        roundService.armRound1("ABC123", 1);

        verify(gameRedisService, never()).getJoinAckCount(any());
        verify(messagingTemplate).convertAndSend(eq("/topic/game.ABC123"), eq(msg));
    }

    @Test
    void armRound1_allPlayersAlreadySubscribed_startsImmediately() {
        when(gameRedisService.getJoinAckCount("ABC123")).thenReturn(2L);
        when(gameRedisService.claimRoundStart("ABC123", 1)).thenReturn(true);
        RoundStartMessage msg = new RoundStartMessage(1, 3, 1L, "url", List.of("a", "b", "c", "d"), 15, Map.of());
        when(gameService.buildRoundStartForRound("ABC123", 1)).thenReturn(msg);

        roundService.armRound1("ABC123", 2);

        verify(messagingTemplate).convertAndSend(eq("/topic/game.ABC123"), eq(msg));
        verify(timerExecutor, never()).schedule(any(Runnable.class), eq(5L), any());
    }

    @Test
    void armRound1_notAllPlayersSubscribedYet_schedulesJoinTimeoutFallbackInstead() {
        when(gameRedisService.getJoinAckCount("ABC123")).thenReturn(1L);

        roundService.armRound1("ABC123", 2);

        verify(gameRedisService, never()).claimRoundStart(any(), anyInt());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
        verify(timerExecutor).schedule(any(Runnable.class), eq(5L), eq(TimeUnit.SECONDS));
    }

    @Test
    void handleJoinAck_gameNotStartedYet_doesNotTriggerRoundStart() {
        // A room where every player happens to connect before the host clicks Start
        // must not consume round 1's idempotency claim early.
        when(gameRedisService.incrementJoinAck("ABC123")).thenReturn(2L);

        roundService.handleJoinAck("ABC123", "tok", false, 2);

        verify(gameRedisService, never()).claimRoundStart(any(), anyInt());
    }

    @Test
    void handleJoinAck_gameInProgressAndQuorumReached_startsRound1() {
        when(gameRedisService.incrementJoinAck("ABC123")).thenReturn(2L);
        when(gameRedisService.claimRoundStart("ABC123", 1)).thenReturn(true);
        RoundStartMessage msg = new RoundStartMessage(1, 3, 1L, "url", List.of("a", "b", "c", "d"), 15, Map.of());
        when(gameService.buildRoundStartForRound("ABC123", 1)).thenReturn(msg);

        roundService.handleJoinAck("ABC123", "tok", true, 2);

        verify(messagingTemplate).convertAndSend(eq("/topic/game.ABC123"), eq(msg));
    }

    @Test
    void handleJoinAck_gameInProgressButQuorumNotReached_doesNotStartRound() {
        when(gameRedisService.incrementJoinAck("ABC123")).thenReturn(1L);

        roundService.handleJoinAck("ABC123", "tok", true, 2);

        verify(gameRedisService, never()).claimRoundStart(any(), anyInt());
    }

    @Test
    void handleJoinAck_alwaysClearsDisconnectFlagForReconnectingPlayer() {
        // A player who reconnects (heartbeat-triggered, brief network blip, etc.) must have
        // their stale disconnect flag cleared immediately — otherwise DisconnectEventListener's
        // 60s forfeit timer can still fire later even though they're back and playing (#36).
        // Must clear regardless of whether the game has started yet or quorum was reached.
        when(gameRedisService.incrementJoinAck("ABC123")).thenReturn(1L);

        roundService.handleJoinAck("ABC123", "tok", false, 2);

        verify(gameRedisService).clearDisconnect("ABC123", "tok");
    }

    @Test
    void handleReady_duplicateFromSamePlayer_doesNotIncrementOrCount() {
        // A client re-sending game.ready must not be counted twice — otherwise one
        // player alone could artificially reach the ready quorum and force the next
        // round to start before the other player has actually readied up (issue #25).
        when(gameRedisService.getCurrentRound("ABC123")).thenReturn(1);
        when(gameRedisService.markReady("ABC123", 1, "tok")).thenReturn(false);

        roundService.handleReady(new ReadyMessage("ABC123"), "tok", 2);

        verify(gameRedisService, never()).incrementReady(any(), anyInt());
        verify(gameRedisService, never()).claimRoundStart(any(), anyInt());
    }

    @Test
    void handleReady_firstTimeFromPlayer_incrementsReadyCount() {
        when(gameRedisService.getCurrentRound("ABC123")).thenReturn(1);
        when(gameRedisService.markReady("ABC123", 1, "tok")).thenReturn(true);
        when(gameRedisService.incrementReady("ABC123", 1)).thenReturn(1L);

        roundService.handleReady(new ReadyMessage("ABC123"), "tok", 2);

        verify(gameRedisService).incrementReady("ABC123", 1);
        verify(gameRedisService, never()).claimRoundStart(any(), anyInt());
    }

    @Test
    void handleReady_quorumReachedByDistinctPlayers_startsNextRound() {
        when(gameRedisService.getCurrentRound("ABC123")).thenReturn(1);
        when(gameRedisService.markReady("ABC123", 1, "tok2")).thenReturn(true);
        when(gameRedisService.incrementReady("ABC123", 1)).thenReturn(2L);
        when(gameRedisService.claimRoundStart("ABC123", 1)).thenReturn(true);
        RoundStartMessage msg = new RoundStartMessage(1, 3, 1L, "url", List.of("a", "b", "c", "d"), 15, Map.of());
        when(gameService.buildRoundStartForRound("ABC123", 1)).thenReturn(msg);

        roundService.handleReady(new ReadyMessage("ABC123"), "tok2", 2);

        verify(messagingTemplate).convertAndSend(eq("/topic/game.ABC123"), eq(msg));
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
