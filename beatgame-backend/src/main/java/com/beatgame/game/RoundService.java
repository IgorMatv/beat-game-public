package com.beatgame.game;

import com.beatgame.player.Player;
import com.beatgame.player.PlayerRepository;
import com.beatgame.room.Room;
import com.beatgame.room.RoomRepository;
import com.beatgame.room.RoomStatus;
import com.beatgame.track.Track;
import com.beatgame.track.TrackRepository;
import com.beatgame.websocket.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Service
public class RoundService {

    private static final Logger log = LoggerFactory.getLogger(RoundService.class);
    private static final int ROUND_SECONDS = 15;
    private static final int READY_TIMEOUT_SECONDS = 30;

    private final GameRedisService gameRedisService;
    private final GameSessionRepository gameSessionRepository;
    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final TrackRepository trackRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ScheduledExecutorService timerExecutor;
    private final GameService gameService;
    private final TransactionTemplate transactionTemplate;

    private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingTimers = new ConcurrentHashMap<>();

    public RoundService(GameRedisService gameRedisService,
                        GameSessionRepository gameSessionRepository,
                        RoomRepository roomRepository,
                        PlayerRepository playerRepository,
                        TrackRepository trackRepository,
                        SimpMessagingTemplate messagingTemplate,
                        ScheduledExecutorService gameTimerExecutor,
                        @org.springframework.context.annotation.Lazy GameService gameService,
                        PlatformTransactionManager transactionManager) {
        this.gameRedisService = gameRedisService;
        this.gameSessionRepository = gameSessionRepository;
        this.roomRepository = roomRepository;
        this.playerRepository = playerRepository;
        this.trackRepository = trackRepository;
        this.messagingTemplate = messagingTemplate;
        this.timerExecutor = gameTimerExecutor;
        this.gameService = gameService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public int calculateScore(boolean correct, int timeMs) {
        if (!correct) return 0;
        int maxTime = ROUND_SECONDS * 1000;
        return Math.max(100, (int)(1000.0 * (maxTime - timeMs) / maxTime));
    }

    public void scheduleRoundTimeout(String roomCode, int round, int maxPlayers) {
        ScheduledFuture<?> future = timerExecutor.schedule(
            () -> closeRound(roomCode, round, maxPlayers),
            ROUND_SECONDS, TimeUnit.SECONDS);
        pendingTimers.put(roomCode + ":" + round, future);
    }

    public void submitAnswer(SubmitAnswerMessage msg, String playerToken) {
        int currentRound = gameRedisService.getCurrentRound(msg.roomCode());
        boolean firstAnswer = gameRedisService.markAnswered(msg.roomCode(), currentRound, playerToken);
        if (!firstAnswer) return;

        GameState state = gameRedisService.loadGameState(msg.roomCode());
        if (state == null) return;

        long correctTrackId = state.trackIds()[currentRound - 1];
        if (correctTrackId != msg.trackId()) return; // stale answer from a previous round

        Room room = roomRepository.findByCode(msg.roomCode()).orElseThrow();
        int correctIndex = gameRedisService.getCorrectIndex(msg.roomCode(), currentRound);
        boolean correct = correctIndex >= 0 && msg.answerIndex() == correctIndex;
        int score = calculateScore(correct, msg.timeMs());
        gameRedisService.addScore(msg.roomCode(), playerToken, score);

        long answers = gameRedisService.incrementAnswers(msg.roomCode(), currentRound);
        if (answers >= room.getMaxPlayers()) {
            closeRound(msg.roomCode(), currentRound, room.getMaxPlayers());
        }
    }

    public void closeRound(String roomCode, int round, int maxPlayers) {
        if (!gameRedisService.claimRoundClose(roomCode, round)) return;
        cancelTimer(roomCode, round);

        GameState state = gameRedisService.loadGameState(roomCode);
        if (state == null) return;

        try {
            transactionTemplate.execute(status -> {
                Room room = roomRepository.findByCode(roomCode).orElse(null);
                if (room == null) return null;

                List<Player> players = playerRepository.findByRoomId(room.getId());
                List<String> tokens = players.stream().map(Player::getPlayerToken).toList();
                Map<String, Integer> scores = gameRedisService.getAllScores(roomCode, tokens);

                long correctTrackId = state.trackIds()[round - 1];
                Track correctTrack = trackRepository.findById(correctTrackId)
                    .orElseThrow(() -> new IllegalStateException("Track not found: " + correctTrackId));
                String correctAnswer = correctTrack.getTitle() + " — " + correctTrack.getArtist();

                messagingTemplate.convertAndSend("/topic/game." + roomCode,
                    new RoundResultMessage(round, correctTrackId, correctAnswer, scores));

                if (round >= state.totalRounds()) {
                    endGame(roomCode, room, scores, tokens);
                } else {
                    gameRedisService.setCurrentRound(roomCode, round + 1);
                    scheduleReadyTimeout(roomCode, round + 1, maxPlayers);
                }
                return null;
            });
        } catch (Exception e) {
            log.error("closeRound failed for room {} round {}: {}", roomCode, round, e.getMessage(), e);
        }
    }

    public void handleReady(ReadyMessage msg, String playerToken, int maxPlayers) {
        int currentRound = gameRedisService.getCurrentRound(msg.roomCode());
        long readyCount = gameRedisService.incrementReady(msg.roomCode(), currentRound);
        if (readyCount >= maxPlayers) {
            startNextRound(msg.roomCode(), currentRound, maxPlayers);
        }
    }

    private void startNextRound(String roomCode, int round, int maxPlayers) {
        try {
            if (!gameRedisService.claimRoundStart(roomCode, round)) return;
            RoundStartMessage msg = gameService.buildRoundStartForRound(roomCode, round);
            messagingTemplate.convertAndSend("/topic/game." + roomCode, msg);
            scheduleRoundTimeout(roomCode, round, maxPlayers);
        } catch (Exception e) {
            log.error("startNextRound failed for room {} round {}: {}", roomCode, round, e.getMessage(), e);
        }
    }

    private void scheduleReadyTimeout(String roomCode, int nextRound, int maxPlayers) {
        ScheduledFuture<?> future = timerExecutor.schedule(
            () -> startNextRound(roomCode, nextRound, maxPlayers),
            READY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        pendingTimers.put(roomCode + ":ready:" + nextRound, future);
    }

    private void cancelAllTimersForRoom(String roomCode) {
        pendingTimers.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(roomCode + ":")) {
                entry.getValue().cancel(false);
                return true;
            }
            return false;
        });
    }

    private void endGame(String roomCode, Room room, Map<String, Integer> scores, List<String> tokens) {
        cancelAllTimersForRoom(roomCode);

        String winnerPlayerToken = tokens.stream()
            .max(Comparator.comparingInt(t -> scores.getOrDefault(t, 0)))
            .orElse(null);

        if (winnerPlayerToken == null) {
            log.warn("No winner found for room {} — token list was empty", roomCode);
        }

        // Delay so clients can see the last round result before navigating to game over
        GameOverMessage gameOverMsg = new GameOverMessage(scores, winnerPlayerToken);
        ScheduledFuture<?> future = timerExecutor.schedule(
            () -> messagingTemplate.convertAndSend("/topic/game." + roomCode, gameOverMsg),
            4, TimeUnit.SECONDS);
        pendingTimers.put(roomCode + ":gameover", future);

        room.setStatus(RoomStatus.FINISHED);
        roomRepository.save(room);

        gameSessionRepository.findTopByRoomIdOrderByStartedAtDesc(room.getId())
            .ifPresent(session -> {
                session.setFinishedAt(LocalDateTime.now());
                gameSessionRepository.save(session);
            });

        gameRedisService.clearGameData(roomCode);
    }

    private void cancelTimer(String roomCode, int round) {
        ScheduledFuture<?> future = pendingTimers.remove(roomCode + ":" + round);
        if (future != null) future.cancel(false);
    }
}
