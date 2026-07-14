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
        when(trackRepository.findDistractorsByGenre(any(), any(), anyInt())).thenReturn(List.of(track(10L), track(11L), track(12L)));

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
        when(trackRepository.findDistractorsByGenre(any(), any(), anyInt())).thenReturn(List.of(track(10L), track(11L), track(12L)));

        gameService.startGame(new StartGameMessage("ABC123", 3, "POP", "GENRE"), "host-tok");

        ArgumentCaptor<GameState> captor = ArgumentCaptor.forClass(GameState.class);
        verify(gameRedisService).storeGameState(eq("ABC123"), captor.capture());
        assertThat(captor.getValue().totalRounds()).isEqualTo(3);
        assertThat(captor.getValue().trackIds()).containsExactly(1L, 2L, 3L);
    }

    @Test
    void startGame_sendsRoundStartMessage() {
        Room room = roomWithCode("ABC123");
        Player host = hostPlayer(room, "host-tok");
        when(roomRepository.findByCode("ABC123")).thenReturn(Optional.of(room));
        when(playerRepository.findByPlayerToken("host-tok")).thenReturn(Optional.of(host));
        when(playerRepository.findByRoomId(any())).thenReturn(List.of(host));
        when(trackService.getTracksForCategory("POP", "GENRE", 3)).thenReturn(List.of(track(1L), track(2L), track(3L)));
        when(gameSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(trackRepository.findDistractorsByGenre(any(), any(), anyInt())).thenReturn(List.of(track(10L), track(11L), track(12L)));

        gameService.startGame(new StartGameMessage("ABC123", 3, "POP", "GENRE"), "host-tok");

        ArgumentCaptor<RoundStartMessage> captor = ArgumentCaptor.forClass(RoundStartMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/game.ABC123"), captor.capture());
        assertThat(captor.getValue().roundNumber()).isEqualTo(1);
        assertThat(captor.getValue().totalRounds()).isEqualTo(3);
        assertThat(captor.getValue().options()).hasSize(4);
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
