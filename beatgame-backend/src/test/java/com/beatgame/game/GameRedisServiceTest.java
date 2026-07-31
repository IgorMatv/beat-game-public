package com.beatgame.game;

import com.beatgame.player.Player;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameRedisServiceTest {

    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock HashOperations<String, String, String> hashOps;
    GameRedisService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOps);
        service = new GameRedisService(redisTemplate,
            new ObjectMapper().registerModule(new ParameterNamesModule()));
    }

    @Test
    void storeAndLoadGameState_roundtrip() throws Exception {
        GameState state = new GameState(5, new Long[]{1L, 2L, 3L}, "POP", "GENRE");
        when(valueOps.get("game:ABC123")).thenAnswer(inv -> {
            String json = new ObjectMapper().writeValueAsString(state);
            return json;
        });

        service.storeGameState("ABC123", state);
        GameState loaded = service.loadGameState("ABC123");

        verify(valueOps).set(eq("game:ABC123"), anyString());
        assertThat(loaded.totalRounds()).isEqualTo(5);
        assertThat(loaded.category()).isEqualTo("POP");
        assertThat(loaded.trackIds()).containsExactly(1L, 2L, 3L);
    }

    @Test
    void setAndGetCurrentRound() {
        when(valueOps.get("round:ABC123")).thenReturn("3");

        service.setCurrentRound("ABC123", 3);
        int round = service.getCurrentRound("ABC123");

        verify(valueOps).set("round:ABC123", "3");
        assertThat(round).isEqualTo(3);
    }

    @Test
    void markAnswered_returnsTrueFirstTime() {
        when(valueOps.setIfAbsent("answered:ABC123:1:tok", "1")).thenReturn(true);

        boolean first = service.markAnswered("ABC123", 1, "tok");

        assertThat(first).isTrue();
    }

    @Test
    void markAnswered_returnsFalseSecondTime() {
        when(valueOps.setIfAbsent("answered:ABC123:1:tok", "1")).thenReturn(false);

        boolean second = service.markAnswered("ABC123", 1, "tok");

        assertThat(second).isFalse();
    }

    @Test
    void markReady_returnsTrueFirstTime() {
        when(valueOps.setIfAbsent("readied:ABC123:1:tok", "1")).thenReturn(true);

        boolean first = service.markReady("ABC123", 1, "tok");

        assertThat(first).isTrue();
    }

    @Test
    void markReady_returnsFalseSecondTime() {
        when(valueOps.setIfAbsent("readied:ABC123:1:tok", "1")).thenReturn(false);

        boolean second = service.markReady("ABC123", 1, "tok");

        assertThat(second).isFalse();
    }

    @Test
    void addScore_setsInitialAndAdds() {
        when(valueOps.get("score:ABC123:tok")).thenReturn("750");

        service.addScore("ABC123", "tok", 750);
        int score = service.getScore("ABC123", "tok");

        verify(valueOps).increment("score:ABC123:tok", 750L);
        assertThat(score).isEqualTo(750);
    }

    @Test
    void markDisconnected_setsKeyWithTtl() {
        service.markDisconnected("ABC123", "tok");

        verify(valueOps).set("disconnect:ABC123:tok", "1", 60, TimeUnit.SECONDS);
        verify(redisTemplate, never()).expire(any(), anyLong(), any());
    }

    @Test
    void isDisconnected_returnsTrueWhenKeyExists() {
        when(redisTemplate.hasKey("disconnect:ABC123:tok")).thenReturn(true);

        assertThat(service.isDisconnected("ABC123", "tok")).isTrue();
    }

    @Test
    void clearDisconnect_deletesKey() {
        service.clearDisconnect("ABC123", "tok");

        verify(redisTemplate).delete("disconnect:ABC123:tok");
    }

    @Test
    void incrementJoinAck_incrementsCounter() {
        when(valueOps.increment("joinack:ABC123")).thenReturn(2L);

        long count = service.incrementJoinAck("ABC123");

        assertThat(count).isEqualTo(2);
    }

    @Test
    void getJoinAckCount_returnsZeroWhenKeyMissing() {
        when(valueOps.get("joinack:ABC123")).thenReturn(null);

        assertThat(service.getJoinAckCount("ABC123")).isZero();
    }

    @Test
    void getJoinAckCount_returnsStoredValue() {
        when(valueOps.get("joinack:ABC123")).thenReturn("2");

        assertThat(service.getJoinAckCount("ABC123")).isEqualTo(2);
    }

    @Test
    void markRoundStarted_setsTimestampKey() {
        service.markRoundStarted("ABC123", 1);

        verify(valueOps).set(eq("roundstartedat:ABC123:1"), anyString());
    }

    @Test
    void getRoundRemainingSeconds_noTimestamp_returnsFullDuration() {
        when(valueOps.get("roundstartedat:ABC123:1")).thenReturn(null);

        int remaining = service.getRoundRemainingSeconds("ABC123", 1, 15);

        assertThat(remaining).isEqualTo(15);
    }

    @Test
    void getRoundRemainingSeconds_partiallyElapsed_returnsReducedValue() {
        long startedAt = System.currentTimeMillis() - 6000;
        when(valueOps.get("roundstartedat:ABC123:1")).thenReturn(String.valueOf(startedAt));

        int remaining = service.getRoundRemainingSeconds("ABC123", 1, 15);

        assertThat(remaining).isBetween(8, 9);
    }

    @Test
    void getRoundRemainingSeconds_fullyElapsed_clampsToZero() {
        long startedAt = System.currentTimeMillis() - 20000;
        when(valueOps.get("roundstartedat:ABC123:1")).thenReturn(String.valueOf(startedAt));

        int remaining = service.getRoundRemainingSeconds("ABC123", 1, 15);

        assertThat(remaining).isZero();
    }

    @Test
    void markReadyStarted_setsTimestampKey() {
        service.markReadyStarted("ABC123", 2);

        verify(valueOps).set(eq("readystartedat:ABC123:2"), anyString());
    }

    @Test
    void getReadyRemainingSeconds_noTimestamp_returnsFullDuration() {
        when(valueOps.get("readystartedat:ABC123:2")).thenReturn(null);

        int remaining = service.getReadyRemainingSeconds("ABC123", 2, 30);

        assertThat(remaining).isEqualTo(30);
    }

    @Test
    void getReadyRemainingSeconds_partiallyElapsed_returnsReducedValue() {
        long startedAt = System.currentTimeMillis() - 10_000;
        when(valueOps.get("readystartedat:ABC123:2")).thenReturn(String.valueOf(startedAt));

        int remaining = service.getReadyRemainingSeconds("ABC123", 2, 30);

        assertThat(remaining).isBetween(19, 20);
    }

    @Test
    void getReadyRemainingSeconds_fullyElapsed_clampsToZero() {
        long startedAt = System.currentTimeMillis() - 40_000;
        when(valueOps.get("readystartedat:ABC123:2")).thenReturn(String.valueOf(startedAt));

        int remaining = service.getReadyRemainingSeconds("ABC123", 2, 30);

        assertThat(remaining).isZero();
    }

    @Test
    void roundHasStarted_keyPresent_returnsTrue() {
        when(redisTemplate.hasKey("roundstartedat:ABC123:1")).thenReturn(true);

        assertThat(service.roundHasStarted("ABC123", 1)).isTrue();
    }

    @Test
    void roundHasStarted_keyAbsent_returnsFalse() {
        when(redisTemplate.hasKey("roundstartedat:ABC123:1")).thenReturn(false);

        assertThat(service.roundHasStarted("ABC123", 1)).isFalse();
    }

    @Test
    void roundIsClosed_keyPresent_returnsTrue() {
        when(redisTemplate.hasKey("roundclosed:ABC123:1")).thenReturn(true);

        assertThat(service.roundIsClosed("ABC123", 1)).isTrue();
    }

    @Test
    void readyPhaseStarted_keyPresent_returnsTrue() {
        when(redisTemplate.hasKey("readystartedat:ABC123:2")).thenReturn(true);

        assertThat(service.readyPhaseStarted("ABC123", 2)).isTrue();
    }

    @Test
    void findRoomCodesWithActiveGame_returnsRoomCodesFromGameKeys() {
        // Cursor.forEachRemaining is a default java.util.Iterator method — must use
        // CALLS_REAL_METHODS so it actually loops hasNext()/next() instead of being a
        // no-op mocked method itself.
        org.springframework.data.redis.core.Cursor<String> cursor =
            mock(org.springframework.data.redis.core.Cursor.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        when(cursor.hasNext()).thenReturn(true, true, false);
        when(cursor.next()).thenReturn("game:ABC123", "game:XYZ999");
        when(redisTemplate.scan(any(org.springframework.data.redis.core.ScanOptions.class))).thenReturn(cursor);

        java.util.Set<String> roomCodes = service.findRoomCodesWithActiveGame();

        assertThat(roomCodes).containsExactlyInAnyOrder("ABC123", "XYZ999");
    }

    @Test
    void storePreviewUrls_putsAllEntriesIntoRoomScopedHash() {
        service.storePreviewUrls("ABC123", Map.of("101", "https://preview1.mp3", "202", "https://preview2.mp3"));

        verify(hashOps).putAll("previews:ABC123", Map.of("101", "https://preview1.mp3", "202", "https://preview2.mp3"));
    }

    @Test
    void storePreviewUrls_doesNothing_whenMapEmpty() {
        service.storePreviewUrls("ABC123", Map.of());

        verify(hashOps, never()).putAll(any(), any());
    }

    @Test
    void getPreviewUrl_returnsStoredValue() {
        when(hashOps.get("previews:ABC123", "101")).thenReturn("https://preview1.mp3");

        assertThat(service.getPreviewUrl("ABC123", "101")).isEqualTo("https://preview1.mp3");
    }

    @Test
    void getPreviewUrl_returnsNull_whenNotResolved() {
        when(hashOps.get("previews:ABC123", "999")).thenReturn(null);

        assertThat(service.getPreviewUrl("ABC123", "999")).isNull();
    }

    @Test
    void getScoresByPlayerId_keysScoresByPlayerIdNotToken() {
        // Broadcast payloads must never key scores by the raw playerToken — another
        // player could read it straight out of the message and hijack that session
        // (issue #24). playerId is a public, non-secret identifier, safe to broadcast.
        Player host = new Player();
        host.setPlayerToken("host-tok");
        ReflectionTestUtils.setField(host, "id", 1L);
        Player guest = new Player();
        guest.setPlayerToken("guest-tok");
        ReflectionTestUtils.setField(guest, "id", 2L);
        when(valueOps.get("score:ABC123:host-tok")).thenReturn("500");
        when(valueOps.get("score:ABC123:guest-tok")).thenReturn("300");

        Map<String, Integer> scores = service.getScoresByPlayerId("ABC123", List.of(host, guest));

        assertThat(scores).containsExactlyInAnyOrderEntriesOf(Map.of("1", 500, "2", 300));
        assertThat(scores).doesNotContainKey("host-tok");
        assertThat(scores).doesNotContainKey("guest-tok");
    }
}
