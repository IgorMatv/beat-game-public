package com.beatgame.game;

import com.beatgame.metrics.GameMetrics;
import com.beatgame.player.Player;
import com.beatgame.player.PlayerRepository;
import com.beatgame.room.Room;
import com.beatgame.room.RoomRepository;
import com.beatgame.room.RoomStatus;
import com.beatgame.track.Track;
import com.beatgame.track.TrackRepository;
import com.beatgame.track.TrackService;
import com.beatgame.track.preview.PreviewUrlResolverService;
import com.beatgame.websocket.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class GameService {

    private final TrackService trackService;
    private final TrackRepository trackRepository;
    private final GameSessionRepository gameSessionRepository;
    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final GameRedisService gameRedisService;
    private final SimpMessagingTemplate messagingTemplate;
    private final RoundService roundService;
    private final ObjectMapper objectMapper;
    private final PreviewUrlResolverService previewUrlResolverService;
    private final GameMetrics gameMetrics;

    public GameService(TrackService trackService,
                       TrackRepository trackRepository,
                       GameSessionRepository gameSessionRepository,
                       RoomRepository roomRepository,
                       PlayerRepository playerRepository,
                       GameRedisService gameRedisService,
                       SimpMessagingTemplate messagingTemplate,
                       RoundService roundService,
                       ObjectMapper objectMapper,
                       PreviewUrlResolverService previewUrlResolverService,
                       GameMetrics gameMetrics) {
        this.trackService = trackService;
        this.trackRepository = trackRepository;
        this.gameSessionRepository = gameSessionRepository;
        this.roomRepository = roomRepository;
        this.playerRepository = playerRepository;
        this.gameRedisService = gameRedisService;
        this.messagingTemplate = messagingTemplate;
        this.roundService = roundService;
        this.objectMapper = objectMapper;
        this.previewUrlResolverService = previewUrlResolverService;
        this.gameMetrics = gameMetrics;
    }

    @Transactional
    public void startGame(StartGameMessage msg, String playerToken, String roomCode) {
        Room room = roomRepository.findByCode(roomCode)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        Player host = playerRepository.findByPlayerToken(playerToken)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Unknown player"));
        if (!host.isHost()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only host can start the game");
        }

        List<Track> tracks = trackService.getTracksForCategory(msg.category(), msg.categoryType(), msg.rounds());
        if (tracks.isEmpty()) {
            messagingTemplate.convertAndSend("/topic/room." + roomCode,
                new StartGameErrorMessage("No tracks available for " + msg.category() + " — try a different category."));
            return;
        }
        Long[] trackIds = tracks.stream().map(Track::getId).toArray(Long[]::new);

        String config;
        try {
            config = objectMapper.writeValueAsString(
                java.util.Map.of("rounds", msg.rounds(), "category", msg.category(), "categoryType", msg.categoryType()));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize room config", e);
        }
        room.setConfig(config);
        room.setStatus(RoomStatus.IN_GAME);
        roomRepository.save(room);
        gameMetrics.incrementGamesStarted();

        GameSession session = new GameSession();
        session.setRoom(room);
        session.setTrackIds(trackIds);
        gameSessionRepository.save(session);

        GameState state = new GameState(msg.rounds(), trackIds, msg.category(), msg.categoryType());
        gameRedisService.storeGameState(roomCode, state);
        gameRedisService.setCurrentRound(roomCode, 1);

        // Resolve every round's preview URL once, up front, instead of live per round-start
        // (issue #5) — a slow/dead provider now only delays this screen, not every round
        // transition. A track whose preview fails to resolve just has no cache entry; its
        // round proceeds with previewUrl=null (frontend shows a "no preview" state).
        Map<String, String> previews = previewUrlResolverService.resolve(tracks);
        gameRedisService.storePreviewUrls(roomCode, previews);

        List<Player> players = playerRepository.findByRoomId(room.getId());
        for (Player p : players) {
            gameRedisService.addScore(roomCode, p.getPlayerToken(), 0);
        }

        roundService.armRound1(roomCode, room.getMaxPlayers());
        broadcastRoomState(roomCode);
    }

    public void broadcastRoomState(String roomCode) {
        Room room = roomRepository.findByCode(roomCode)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
        List<Player> players = playerRepository.findByRoomId(room.getId());
        List<PlayerInfo> infos = players.stream()
            .map(p -> new PlayerInfo(p.getId(), p.getName(), p.isHost()))
            .toList();
        messagingTemplate.convertAndSend("/topic/room." + roomCode, new RoomStateMessage(infos, room.getStatus().name()));
    }

    @Transactional(readOnly = true)
    public RoundStartMessage buildRoundStartForRound(String roomCode, int roundNumber) {
        GameState state = gameRedisService.loadGameState(roomCode);
        if (state == null) throw new IllegalStateException("Game state missing for room " + roomCode + " — game already ended");
        Long trackId = state.trackIds()[roundNumber - 1];
        Track track = trackRepository.findById(trackId)
            .orElseThrow(() -> new IllegalStateException("Track not found: " + trackId));
        int remainingSeconds = gameRedisService.getRoundRemainingSeconds(roomCode, roundNumber, RoundService.ROUND_SECONDS);
        Room room = roomRepository.findByCode(roomCode)
            .orElseThrow(() -> new IllegalStateException("Room not found: " + roomCode));
        List<Player> players = playerRepository.findByRoomId(room.getId());
        Map<String, Integer> scores = gameRedisService.getScoresByPlayerId(roomCode, players);
        return buildRoundStart(roomCode, track, roundNumber, state.totalRounds(), state.category(), state.categoryType(), remainingSeconds, scores);
    }

    private RoundStartMessage buildRoundStart(String roomCode, Track track, int roundNumber, int totalRounds,
                                               String category, String categoryType, int remainingSeconds,
                                               Map<String, Integer> scores) {
        List<String> options = generateOptions(track, category, categoryType);
        String correctOption = track.getTitle() + " — " + track.getArtist();
        gameRedisService.storeCorrectIndex(roomCode, roundNumber, options.indexOf(correctOption));
        String previewUrl = gameRedisService.getPreviewUrl(roomCode, track.getExternalId());
        return new RoundStartMessage(roundNumber, totalRounds, track.getId(), previewUrl, options, remainingSeconds, scores);
    }

    public List<String> generateOptions(Track correctTrack, String category, String categoryType) {
        String correctOption = correctTrack.getTitle() + " — " + correctTrack.getArtist();
        List<String> excludedArtists = List.of(correctTrack.getArtist());

        List<Track> distractors = fetchDistractors(correctTrack, category, categoryType, excludedArtists);

        List<String> options = new ArrayList<>();
        options.add(correctOption);
        for (Track d : distractors) {
            options.add(d.getTitle() + " — " + d.getArtist());
        }
        Collections.shuffle(options);
        return options;
    }

    private List<Track> fetchDistractors(Track correct, String category, String categoryType,
                                          List<String> excludedArtists) {
        List<Track> distractors = switch (categoryType) {
            case "GENRE" -> trackRepository.findDistractorsByGenre(
                correct.getGenre() != null ? correct.getGenre().name() : category,
                excludedArtists, 3);
            case "DECADE" -> trackRepository.findDistractorsByDecade(
                correct.getDecade() != null ? correct.getDecade() : Short.parseShort(category),
                excludedArtists, 3);
            default -> trackRepository.findDistractorsFromAll(excludedArtists, 3);
        };

        if (distractors.size() < 3) {
            List<String> allExcluded = new ArrayList<>(excludedArtists);
            distractors.forEach(d -> allExcluded.add(d.getArtist()));
            List<Track> extra = trackRepository.findDistractorsFromAll(allExcluded, 3 - distractors.size());
            distractors = new ArrayList<>(distractors);
            distractors.addAll(extra);
        }
        if (distractors.size() < 3) {
            throw new IllegalStateException(
                "Not enough distractors for room — need 3, found " + distractors.size() +
                ". Add more tracks to the database.");
        }
        return distractors;
    }

    @Transactional
    public void resetRoom(String roomCode) {
        Room room = roomRepository.findByCode(roomCode)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
        room.setStatus(RoomStatus.WAITING);
        roomRepository.save(room);
        gameRedisService.clearGameData(roomCode);
        broadcastRoomState(roomCode);
    }

    @Transactional(readOnly = true)
    public void handleSync(String roomCode) {
        Room room = roomRepository.findByCode(roomCode).orElse(null);
        if (room == null || room.getStatus() != RoomStatus.IN_GAME) return;
        if (gameRedisService.loadGameState(roomCode) == null) return;
        int currentRound = gameRedisService.getCurrentRound(roomCode);
        RoundStartMessage roundStart = buildRoundStartForRound(roomCode, currentRound);
        messagingTemplate.convertAndSend("/topic/game." + roomCode, roundStart);
    }
}
