package com.beatgame.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameRedisServiceTest {

    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    GameRedisService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
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
}
