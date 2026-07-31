package com.beatgame.game;

import com.beatgame.player.Player;
import com.beatgame.player.PlayerRepository;
import com.beatgame.room.Room;
import com.beatgame.room.RoomRepository;
import com.beatgame.room.RoomStatus;
import com.beatgame.track.Genre;
import com.beatgame.track.Track;
import com.beatgame.track.TrackRepository;
import com.beatgame.track.TrackService;
import com.beatgame.track.preview.PreviewUrlResolverService;
import com.beatgame.websocket.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock TrackService trackService;
    @Mock TrackRepository trackRepository;
    @Mock GameSessionRepository gameSessionRepository;
    @Mock RoomRepository roomRepository;
    @Mock PlayerRepository playerRepository;
    @Mock GameRedisService gameRedisService;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock RoundService roundService;
    @Mock PreviewUrlResolverService previewUrlResolverService;

    GameService gameService;

    @BeforeEach
    void setUp() {
        gameService = new GameService(trackService, trackRepository, gameSessionRepository,
            roomRepository, playerRepository, gameRedisService, messagingTemplate, roundService,
            new ObjectMapper().registerModule(new ParameterNamesModule()), previewUrlResolverService);
    }

    @Test
    void startGame_setsRoomStatusToInGame() {
        Room room = roomWithCode("ABC123");
        Player host = hostPlayer(room, "host-tok");
        when(roomRepository.findByCode("ABC123")).thenReturn(Optional.of(room));
        when(playerRepository.findByPlayerToken("host-tok")).thenReturn(Optional.of(host));
        when(playerRepository.findByRoomId(any())).thenReturn(List.of(host));
        when(trackService.getTracksForCategory("POP", "GENRE", 3)).thenReturn(List.of(track(1L), track(2L), track(3L)));
        when(gameSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        gameService.startGame(new StartGameMessage("ABC123", 3, "POP", "GENRE"), "host-tok");

        assertThat(room.getStatus()).isEqualTo(RoomStatus.IN_GAME);
        verify(roomRepository).save(room);
    }

    @Test
    void startGame_storesGameStateInRedis() {
        Room room = roomWithCode("ABC123");
        Player host = hostPlayer(room, "host-tok");
        Track t1 = track(1L); Track t2 = track(2L); Track t3 = track(3L);
        when(roomRepository.findByCode("ABC123")).thenReturn(Optional.of(room));
        when(playerRepository.findByPlayerToken("host-tok")).thenReturn(Optional.of(host));
        when(playerRepository.findByRoomId(any())).thenReturn(List.of(host));
        when(trackService.getTracksForCategory("POP", "GENRE", 3)).thenReturn(List.of(t1, t2, t3));
        when(gameSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        gameService.startGame(new StartGameMessage("ABC123", 3, "POP", "GENRE"), "host-tok");

        ArgumentCaptor<GameState> captor = ArgumentCaptor.forClass(GameState.class);
        verify(gameRedisService).storeGameState(eq("ABC123"), captor.capture());
        assertThat(captor.getValue().totalRounds()).isEqualTo(3);
        assertThat(captor.getValue().trackIds()).containsExactly(1L, 2L, 3L);
    }

    @Test
    void startGame_resolvesAndCachesPreviewsForAllTracksUpFront() {
        // Preview URLs are resolved once for the whole game here, not live per round-start
        // (issue #5) — a slow/dead provider then only delays this screen, not every round.
        Room room = roomWithCode("ABC123");
        Player host = hostPlayer(room, "host-tok");
        Track t1 = track(1L); Track t2 = track(2L); Track t3 = track(3L);
        when(roomRepository.findByCode("ABC123")).thenReturn(Optional.of(room));
        when(playerRepository.findByPlayerToken("host-tok")).thenReturn(Optional.of(host));
        when(playerRepository.findByRoomId(any())).thenReturn(List.of(host));
        when(trackService.getTracksForCategory("POP", "GENRE", 3)).thenReturn(List.of(t1, t2, t3));
        when(gameSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Map<String, String> previews = Map.of("1", "https://preview1.mp3");
        when(previewUrlResolverService.resolve(List.of(t1, t2, t3))).thenReturn(previews);

        gameService.startGame(new StartGameMessage("ABC123", 3, "POP", "GENRE"), "host-tok");

        verify(gameRedisService).storePreviewUrls("ABC123", previews);
    }

    @Test
    void startGame_delegatesRound1StartToRoundService() {
        // Round 1's broadcast is gated behind RoundService.armRound1 (issue #34 fix) rather
        // than sent directly, so every player's WS subscription can be confirmed first.
        Room room = roomWithCode("ABC123");
        Player host = hostPlayer(room, "host-tok");
        when(roomRepository.findByCode("ABC123")).thenReturn(Optional.of(room));
        when(playerRepository.findByPlayerToken("host-tok")).thenReturn(Optional.of(host));
        when(playerRepository.findByRoomId(any())).thenReturn(List.of(host));
        when(trackService.getTracksForCategory("POP", "GENRE", 3)).thenReturn(List.of(track(1L), track(2L), track(3L)));
        when(gameSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        gameService.startGame(new StartGameMessage("ABC123", 3, "POP", "GENRE"), "host-tok");

        verify(roundService).armRound1("ABC123", 2);
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/game.ABC123"), any(RoundStartMessage.class));
    }

    @Test
    void startGame_noTracksForCategory_sendsErrorAndDoesNotMutateRoomState() {
        // A category can legitimately have zero active tracks (e.g. UKRAINIAN before its
        // one-time admin populate call, or any genre/decade where both providers failed
        // an on-demand fetch). Previously this crashed unguarded with an
        // IndexOutOfBoundsException that was only logged server-side — the host just saw
        // nothing happen (issue #18).
        Room room = roomWithCode("ABC123");
        Player host = hostPlayer(room, "host-tok");
        when(roomRepository.findByCode("ABC123")).thenReturn(Optional.of(room));
        when(playerRepository.findByPlayerToken("host-tok")).thenReturn(Optional.of(host));
        when(trackService.getTracksForCategory("UKRAINIAN", "GENRE", 3)).thenReturn(List.of());

        gameService.startGame(new StartGameMessage("ABC123", 3, "UKRAINIAN", "GENRE"), "host-tok");

        ArgumentCaptor<StartGameErrorMessage> captor = ArgumentCaptor.forClass(StartGameErrorMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/room.ABC123"), captor.capture());
        assertThat(captor.getValue().reason()).contains("UKRAINIAN");
        assertThat(room.getStatus()).isEqualTo(RoomStatus.WAITING);
        verify(roomRepository, never()).save(any());
        verify(roundService, never()).armRound1(any(), anyInt());
    }

    @Test
    void generateOptions_returnsExactlyFourShuffled() {
        Track correct = trackWithDetails(1L, "Song A", "Artist A", Genre.POP, null);
        Track d1 = trackWithDetails(2L, "Song B", "Artist B", Genre.POP, null);
        Track d2 = trackWithDetails(3L, "Song C", "Artist C", Genre.POP, null);
        Track d3 = trackWithDetails(4L, "Song D", "Artist D", Genre.POP, null);
        when(trackRepository.findDistractorsByGenre(eq("POP"), any(), eq(3))).thenReturn(List.of(d1, d2, d3));

        List<String> options = gameService.generateOptions(correct, "POP", "GENRE");

        assertThat(options).hasSize(4);
        assertThat(options).contains("Song A — Artist A");
        assertThat(options).contains("Song B — Artist B");
    }

    @Test
    void generateOptions_fallsBackToAllTracksWhenNotEnoughDistractors() {
        Track correct = trackWithDetails(1L, "Song A", "Artist A", Genre.POP, null);
        Track fb1 = trackWithDetails(5L, "Song E", "Artist E", Genre.ROCK, null);
        Track fb2 = trackWithDetails(6L, "Song F", "Artist F", Genre.ROCK, null);
        Track fb3 = trackWithDetails(7L, "Song G", "Artist G", Genre.ROCK, null);
        when(trackRepository.findDistractorsByGenre(any(), any(), eq(3))).thenReturn(List.of());
        when(trackRepository.findDistractorsFromAll(any(), eq(3))).thenReturn(List.of(fb1, fb2, fb3));

        List<String> options = gameService.generateOptions(correct, "POP", "GENRE");

        assertThat(options).hasSize(4);
        assertThat(options).contains("Song A — Artist A");
        assertThat(options).contains("Song E — Artist E");
        assertThat(options).contains("Song F — Artist F");
        assertThat(options).contains("Song G — Artist G");
    }

    @Test
    void broadcastRoomState_sendsToRoomTopic() {
        Room room = roomWithCode("ABC123");
        Player p = hostPlayer(room, "tok");
        when(roomRepository.findByCode("ABC123")).thenReturn(Optional.of(room));
        when(playerRepository.findByRoomId(any())).thenReturn(List.of(p));

        gameService.broadcastRoomState("ABC123");

        ArgumentCaptor<RoomStateMessage> captor = ArgumentCaptor.forClass(RoomStateMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/room.ABC123"), captor.capture());
        assertThat(captor.getValue().players()).hasSize(1);
    }

    @Test
    void buildRoundStartForRound_noTimestampYet_returnsFullRoundDurationAndCurrentScores() {
        Room room = roomWithCode("ABC123");
        Player host = hostPlayer(room, "host-tok");
        GameState state = new GameState(3, new Long[]{1L, 2L, 3L}, "POP", "GENRE");
        Track track = trackWithDetails(1L, "Song A", "Artist A", Genre.POP, null);
        when(roomRepository.findByCode("ABC123")).thenReturn(Optional.of(room));
        when(playerRepository.findByRoomId(any())).thenReturn(List.of(host));
        when(gameRedisService.loadGameState("ABC123")).thenReturn(state);
        when(trackRepository.findById(1L)).thenReturn(Optional.of(track));
        when(trackRepository.findDistractorsByGenre(any(), any(), anyInt())).thenReturn(List.of(track(10L), track(11L), track(12L)));
        when(gameRedisService.getRoundRemainingSeconds("ABC123", 1, RoundService.ROUND_SECONDS)).thenReturn(RoundService.ROUND_SECONDS);
        when(gameRedisService.getScoresByPlayerId(eq("ABC123"), any())).thenReturn(Map.of("10", 0));

        RoundStartMessage msg = gameService.buildRoundStartForRound("ABC123", 1);

        assertThat(msg.remainingSeconds()).isEqualTo(RoundService.ROUND_SECONDS);
        assertThat(msg.scores()).containsEntry("10", 0);
    }

    @Test
    void buildRoundStartForRound_readsPreviewUrlFromPreResolvedCache_notLiveResolve() {
        // Preview URLs are resolved once up front at game start (issue #5) — a round build
        // must read the cached value, never call PreviewUrlResolverService itself.
        Room room = roomWithCode("ABC123");
        Player host = hostPlayer(room, "host-tok");
        GameState state = new GameState(3, new Long[]{1L, 2L, 3L}, "POP", "GENRE");
        Track track = trackWithDetails(1L, "Song A", "Artist A", Genre.POP, null);
        when(roomRepository.findByCode("ABC123")).thenReturn(Optional.of(room));
        when(playerRepository.findByRoomId(any())).thenReturn(List.of(host));
        when(gameRedisService.loadGameState("ABC123")).thenReturn(state);
        when(trackRepository.findById(1L)).thenReturn(Optional.of(track));
        when(trackRepository.findDistractorsByGenre(any(), any(), anyInt())).thenReturn(List.of(track(10L), track(11L), track(12L)));
        when(gameRedisService.getRoundRemainingSeconds("ABC123", 1, RoundService.ROUND_SECONDS)).thenReturn(RoundService.ROUND_SECONDS);
        when(gameRedisService.getScoresByPlayerId(eq("ABC123"), any())).thenReturn(Map.of("10", 0));
        when(gameRedisService.getPreviewUrl("ABC123", track.getExternalId())).thenReturn("https://cached-preview.mp3");

        RoundStartMessage msg = gameService.buildRoundStartForRound("ABC123", 1);

        assertThat(msg.previewUrl()).isEqualTo("https://cached-preview.mp3");
        verifyNoInteractions(previewUrlResolverService);
    }

    @Test
    void buildRoundStartForRound_roundAlreadyInProgress_returnsReducedRemainingTimeAndCurrentScores() {
        Room room = roomWithCode("ABC123");
        Player host = hostPlayer(room, "host-tok");
        GameState state = new GameState(3, new Long[]{1L, 2L, 3L}, "POP", "GENRE");
        Track track = trackWithDetails(1L, "Song A", "Artist A", Genre.POP, null);
        when(roomRepository.findByCode("ABC123")).thenReturn(Optional.of(room));
        when(playerRepository.findByRoomId(any())).thenReturn(List.of(host));
        when(gameRedisService.loadGameState("ABC123")).thenReturn(state);
        when(trackRepository.findById(1L)).thenReturn(Optional.of(track));
        when(trackRepository.findDistractorsByGenre(any(), any(), anyInt())).thenReturn(List.of(track(10L), track(11L), track(12L)));
        when(gameRedisService.getRoundRemainingSeconds("ABC123", 1, RoundService.ROUND_SECONDS)).thenReturn(6);
        when(gameRedisService.getScoresByPlayerId(eq("ABC123"), any())).thenReturn(Map.of("10", 250));

        RoundStartMessage msg = gameService.buildRoundStartForRound("ABC123", 1);

        assertThat(msg.remainingSeconds()).isEqualTo(6);
        assertThat(msg.scores()).containsEntry("10", 250);
    }

    @Test
    void handleSync_roomNotFound_doesNothing() {
        when(roomRepository.findByCode("ABC123")).thenReturn(Optional.empty());

        gameService.handleSync("ABC123");

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void handleSync_roomNotInGame_doesNothing() {
        Room room = roomWithCode("ABC123"); // WAITING by default
        when(roomRepository.findByCode("ABC123")).thenReturn(Optional.of(room));

        gameService.handleSync("ABC123");

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void handleSync_gameStateMissing_doesNothing() {
        Room room = roomWithCode("ABC123");
        room.setStatus(RoomStatus.IN_GAME);
        when(roomRepository.findByCode("ABC123")).thenReturn(Optional.of(room));
        when(gameRedisService.loadGameState("ABC123")).thenReturn(null);

        gameService.handleSync("ABC123");

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void handleSync_gameInProgress_broadcastsCurrentRoundWithScores() {
        Room room = roomWithCode("ABC123");
        room.setStatus(RoomStatus.IN_GAME);
        Player host = hostPlayer(room, "host-tok");
        GameState state = new GameState(3, new Long[]{1L, 2L, 3L}, "POP", "GENRE");
        Track track = trackWithDetails(2L, "Song B", "Artist B", Genre.POP, null);
        when(roomRepository.findByCode("ABC123")).thenReturn(Optional.of(room));
        when(playerRepository.findByRoomId(any())).thenReturn(List.of(host));
        when(gameRedisService.loadGameState("ABC123")).thenReturn(state);
        when(gameRedisService.getCurrentRound("ABC123")).thenReturn(2);
        when(trackRepository.findById(2L)).thenReturn(Optional.of(track));
        when(trackRepository.findDistractorsByGenre(any(), any(), anyInt())).thenReturn(List.of(track(10L), track(11L), track(12L)));
        when(gameRedisService.getRoundRemainingSeconds("ABC123", 2, RoundService.ROUND_SECONDS)).thenReturn(9);
        when(gameRedisService.getScoresByPlayerId(eq("ABC123"), any())).thenReturn(Map.of("10", 450));

        gameService.handleSync("ABC123");

        ArgumentCaptor<RoundStartMessage> captor = ArgumentCaptor.forClass(RoundStartMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/game.ABC123"), captor.capture());
        assertThat(captor.getValue().roundNumber()).isEqualTo(2);
        assertThat(captor.getValue().remainingSeconds()).isEqualTo(9);
        assertThat(captor.getValue().scores()).containsEntry("10", 450);
    }

    private Room roomWithCode(String code) {
        Room room = new Room();
        room.setCode(code);
        room.setMaxPlayers((short) 2);
        room.setStatus(RoomStatus.WAITING);
        ReflectionTestUtils.setField(room, "id", 1L);
        return room;
    }

    private Player hostPlayer(Room room, String token) {
        Player p = new Player();
        p.setRoom(room);
        p.setName("Alice");
        p.setHost(true);
        p.setPlayerToken(token);
        ReflectionTestUtils.setField(p, "id", 10L);
        return p;
    }

    private Track track(Long id) {
        return trackWithDetails(id, "Track " + id, "Artist " + id, Genre.POP, null);
    }

    private Track trackWithDetails(Long id, String title, String artist, Genre genre, Short decade) {
        Track t = new Track();
        t.setTitle(title);
        t.setArtist(artist);
        t.setGenre(genre);
        t.setDecade(decade);
        t.setPreviewUrl("https://preview/" + id);
        ReflectionTestUtils.setField(t, "id", id);
        return t;
    }
}
