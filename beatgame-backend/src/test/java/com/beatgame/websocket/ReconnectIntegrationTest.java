package com.beatgame.websocket;

import com.beatgame.game.GameRedisService;
import com.beatgame.game.GameService;
import com.beatgame.game.RoundService;
import com.beatgame.player.Player;
import com.beatgame.player.PlayerRepository;
import com.beatgame.room.Room;
import com.beatgame.room.RoomRepository;
import com.beatgame.room.RoomService;
import com.beatgame.room.RoomStatus;
import com.beatgame.room.dto.CreateRoomResponse;
import com.beatgame.track.Genre;
import com.beatgame.track.Track;
import com.beatgame.track.TrackRepository;
import com.beatgame.websocket.dto.GameOverMessage;
import com.beatgame.websocket.dto.RoundStartMessage;
import com.beatgame.websocket.dto.StartGameMessage;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// Component-level integration coverage for issue #72: disconnect/reconnect spans
// DisconnectEventListener, GameRedisService (real Redis), Postgres room state, and
// RoundService/GameService's reconnect handlers, none of which were exercised together
// end-to-end before -- unit tests mock every one of those collaborators individually.
// Deliberately skips a raw STOMP/WebSocket client: SessionDisconnectEvent is published
// directly (mirroring what Spring's STOMP handling actually delivers to
// DisconnectEventListener) and reconnect is driven through the same handleJoinAck/
// handleSync methods the game.subscribed/game.sync STOMP handlers call, keeping the
// wiring under test real while avoiding a first-of-its-kind wire-level test client.
@SpringBootTest
@Testcontainers
class ReconnectIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        // Real default is 60s; shrink it so "genuine disconnect ends the game" doesn't
        // need a real 60s wait.
        registry.add("disconnect.rejoin-timeout-seconds", () -> "2");
        // Avoid real Deezer/iTunes network calls at context startup.
        registry.add("app.initial-data-seeder.enabled", () -> "false");
    }

    @Autowired RoomService roomService;
    @Autowired RoomRepository roomRepository;
    @Autowired PlayerRepository playerRepository;
    @Autowired TrackRepository trackRepository;
    @Autowired GameService gameService;
    @Autowired RoundService roundService;
    @Autowired GameRedisService gameRedisService;
    @Autowired ApplicationEventPublisher eventPublisher;
    @Autowired MeterRegistry meterRegistry;

    @MockBean SimpMessagingTemplate messagingTemplate;

    String roomCode;
    Player host;
    Player guest;

    @BeforeEach
    void setUp() {
        for (int i = 0; i < 20; i++) {
            Track t = new Track();
            t.setTitle("Song " + i);
            t.setArtist("Artist " + i);
            t.setGenre(Genre.POP);
            t.setDecade((short) 2000);
            // ITUNES avoids PreviewUrlResolverService hitting the real Deezer API.
            t.setProvider("ITUNES");
            t.setExternalId("reconnect-it-" + i);
            t.setPreviewUrl("https://example.com/preview" + i + ".mp3");
            t.setArchived(false);
            trackRepository.save(t);
        }

        CreateRoomResponse created = roomService.createRoom("Host");
        roomCode = created.roomCode();
        roomService.joinRoom(roomCode, "Guest");

        Room room = roomRepository.findByCode(roomCode).orElseThrow();
        List<Player> players = playerRepository.findByRoomId(room.getId());
        host = players.stream().filter(Player::isHost).findFirst().orElseThrow();
        guest = players.stream().filter(p -> !p.isHost()).findFirst().orElseThrow();

        // Both players "subscribed" to the game topic before the host starts, matching
        // production: game.subscribed fires on every connect, not just in-game ones.
        roundService.handleJoinAck(roomCode, host.getPlayerToken(), false, 2);
        roundService.handleJoinAck(roomCode, guest.getPlayerToken(), false, 2);

        gameService.startGame(new StartGameMessage(roomCode, 3, "POP", "GENRE"), host.getPlayerToken(), roomCode);

        reset(messagingTemplate); // ignore setup's own broadcasts (round 1 start, room state)
    }

    @AfterEach
    void tearDown() {
        playerRepository.deleteAll();
        roomRepository.deleteAll();
        trackRepository.deleteAll();
    }

    private SessionDisconnectEvent disconnectEventFor(String playerToken, String roomCode) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.DISCONNECT);
        accessor.setSessionId("test-session-" + playerToken);
        Map<String, Object> sessionAttrs = new HashMap<>();
        sessionAttrs.put("playerToken", playerToken);
        sessionAttrs.put("roomCode", roomCode);
        accessor.setSessionAttributes(sessionAttrs);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return new SessionDisconnectEvent(this, message, accessor.getSessionId(), CloseStatus.NORMAL);
    }

    @Test
    void temporaryDisconnect_thenReconnectAndSync_gameContinues() {
        double reconnectsBefore = meterRegistry.get("beatgame_reconnects_total").counter().count();

        eventPublisher.publishEvent(disconnectEventFor(guest.getPlayerToken(), roomCode));

        assertThat(gameRedisService.isDisconnected(roomCode, guest.getPlayerToken())).isTrue();
        assertThat(roomRepository.findByCode(roomCode).orElseThrow().getStatus()).isEqualTo(RoomStatus.IN_GAME);
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(GameOverMessage.class));

        roundService.handleJoinAck(roomCode, guest.getPlayerToken(), true, 2);

        assertThat(gameRedisService.isDisconnected(roomCode, guest.getPlayerToken())).isFalse();
        assertThat(meterRegistry.get("beatgame_reconnects_total").counter().count()).isEqualTo(reconnectsBefore + 1);

        // Duplicate game.subscribed (e.g. a flaky client retrying) must not double-count —
        // clearDisconnect is already false, so this is a no-op for the metric.
        roundService.handleJoinAck(roomCode, guest.getPlayerToken(), true, 2);
        assertThat(meterRegistry.get("beatgame_reconnects_total").counter().count()).isEqualTo(reconnectsBefore + 1);

        gameService.handleSync(roomCode);

        ArgumentCaptor<RoundStartMessage> captor = ArgumentCaptor.forClass(RoundStartMessage.class);
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/game." + roomCode), captor.capture());
        RoundStartMessage lastBroadcast = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(lastBroadcast.roundNumber()).isEqualTo(1);

        assertThat(roomRepository.findByCode(roomCode).orElseThrow().getStatus()).isEqualTo(RoomStatus.IN_GAME);
    }

    @Test
    void genuineDisconnect_endsGameAfterRejoinWindow() {
        eventPublisher.publishEvent(disconnectEventFor(guest.getPlayerToken(), roomCode));
        assertThat(gameRedisService.isDisconnected(roomCode, guest.getPlayerToken())).isTrue();

        await().atMost(Duration.ofSeconds(6)).untilAsserted(() -> {
            Room room = roomRepository.findByCode(roomCode).orElseThrow();
            assertThat(room.getStatus()).isEqualTo(RoomStatus.FINISHED);
        });

        ArgumentCaptor<GameOverMessage> captor = ArgumentCaptor.forClass(GameOverMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/game." + roomCode), captor.capture());
        assertThat(captor.getValue().winnerPlayerId()).isEqualTo(String.valueOf(host.getId()));

        assertThat(gameRedisService.loadGameState(roomCode)).isNull();
    }
}
