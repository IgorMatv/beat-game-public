package com.beatgame.game;

import com.beatgame.player.Player;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class GameRedisService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public GameRedisService(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void storeGameState(String roomCode, GameState state) {
        try {
            redisTemplate.opsForValue().set("game:" + roomCode, objectMapper.writeValueAsString(state));
        } catch (Exception e) {
            throw new RuntimeException("Failed to store game state", e);
        }
    }

    public GameState loadGameState(String roomCode) {
        try {
            String json = redisTemplate.opsForValue().get("game:" + roomCode);
            if (json == null) return null;
            return objectMapper.readValue(json, GameState.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load game state", e);
        }
    }

    public void setCurrentRound(String roomCode, int round) {
        redisTemplate.opsForValue().set("round:" + roomCode, String.valueOf(round));
    }

    public int getCurrentRound(String roomCode) {
        String val = redisTemplate.opsForValue().get("round:" + roomCode);
        return val == null ? 1 : Integer.parseInt(val);
    }

    public long incrementAnswers(String roomCode, int round) {
        Long result = redisTemplate.opsForValue().increment("answers:" + roomCode + ":" + round);
        return result == null ? 0 : result;
    }

    public boolean markAnswered(String roomCode, int round, String playerToken) {
        Boolean result = redisTemplate.opsForValue()
            .setIfAbsent("answered:" + roomCode + ":" + round + ":" + playerToken, "1");
        return Boolean.TRUE.equals(result);
    }

    public void addScore(String roomCode, String playerToken, int points) {
        redisTemplate.opsForValue().increment("score:" + roomCode + ":" + playerToken, points);
    }

    public int getScore(String roomCode, String playerToken) {
        String val = redisTemplate.opsForValue().get("score:" + roomCode + ":" + playerToken);
        return val == null ? 0 : Integer.parseInt(val);
    }

    public Map<String, Integer> getAllScores(String roomCode, List<String> playerTokens) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        for (String token : playerTokens) {
            scores.put(token, getScore(roomCode, token));
        }
        return scores;
    }

    // Broadcast-safe variant of getAllScores — keys by playerId instead of the raw
    // playerToken, since anything sent to /topic/... is visible to every player in the
    // room (issue #24: a leaked token can be used to hijack that player's session).
    public Map<String, Integer> getScoresByPlayerId(String roomCode, List<Player> players) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        for (Player p : players) {
            scores.put(String.valueOf(p.getId()), getScore(roomCode, p.getPlayerToken()));
        }
        return scores;
    }

    public boolean markReady(String roomCode, int round, String playerToken) {
        Boolean result = redisTemplate.opsForValue()
            .setIfAbsent("readied:" + roomCode + ":" + round + ":" + playerToken, "1");
        return Boolean.TRUE.equals(result);
    }

    public long incrementReady(String roomCode, int round) {
        Long result = redisTemplate.opsForValue().increment("ready:" + roomCode + ":" + round);
        return result == null ? 0 : result;
    }

    public void markRoundStarted(String roomCode, int round) {
        redisTemplate.opsForValue().set("roundstartedat:" + roomCode + ":" + round, String.valueOf(System.currentTimeMillis()));
    }

    public int getRoundRemainingSeconds(String roomCode, int round, int roundSeconds) {
        String val = redisTemplate.opsForValue().get("roundstartedat:" + roomCode + ":" + round);
        if (val == null) return roundSeconds;
        long startedAt = Long.parseLong(val);
        long elapsedMs = System.currentTimeMillis() - startedAt;
        int remaining = roundSeconds - (int) (elapsedMs / 1000);
        return Math.max(remaining, 0);
    }

    // Preview URLs are resolved once, up front, for every track in the game (see
    // GameService.startGame) rather than live per round-start — a slow/dead provider
    // then only delays the "waiting to start" screen, not every round transition
    // (issue #5). Room-scoped so a rematch in the same room starts with a clean cache.
    public void storePreviewUrls(String roomCode, Map<String, String> previewsByExternalId) {
        if (previewsByExternalId.isEmpty()) return;
        redisTemplate.<String, String>opsForHash().putAll("previews:" + roomCode, previewsByExternalId);
    }

    public String getPreviewUrl(String roomCode, String externalId) {
        return redisTemplate.<String, String>opsForHash().get("previews:" + roomCode, externalId);
    }

    public long incrementJoinAck(String roomCode) {
        Long result = redisTemplate.opsForValue().increment("joinack:" + roomCode);
        return result == null ? 0 : result;
    }

    public long getJoinAckCount(String roomCode) {
        String val = redisTemplate.opsForValue().get("joinack:" + roomCode);
        return val == null ? 0 : Long.parseLong(val);
    }

    public boolean claimRoundStart(String roomCode, int round) {
        Boolean result = redisTemplate.opsForValue()
            .setIfAbsent("roundstarted:" + roomCode + ":" + round, "1");
        return Boolean.TRUE.equals(result);
    }

    public void storeCorrectIndex(String roomCode, int round, int index) {
        redisTemplate.opsForValue().set("correctIndex:" + roomCode + ":" + round, String.valueOf(index));
    }

    public int getCorrectIndex(String roomCode, int round) {
        String val = redisTemplate.opsForValue().get("correctIndex:" + roomCode + ":" + round);
        return val != null ? Integer.parseInt(val) : -1;
    }

    public boolean claimRoundClose(String roomCode, int round) {
        Boolean result = redisTemplate.opsForValue()
            .setIfAbsent("roundclosed:" + roomCode + ":" + round, "1");
        return Boolean.TRUE.equals(result);
    }

    public void markDisconnected(String roomCode, String playerToken) {
        redisTemplate.opsForValue().set(
            "disconnect:" + roomCode + ":" + playerToken, "1", 60, TimeUnit.SECONDS);
    }

    public boolean isDisconnected(String roomCode, String playerToken) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("disconnect:" + roomCode + ":" + playerToken));
    }

    public void clearDisconnect(String roomCode, String playerToken) {
        redisTemplate.delete("disconnect:" + roomCode + ":" + playerToken);
    }

    public void clearGameData(String roomCode) {
        redisTemplate.delete("game:" + roomCode);
        redisTemplate.delete("round:" + roomCode);
        deleteByPattern("score:" + roomCode + ":*");
        deleteByPattern("answered:" + roomCode + ":*");
        deleteByPattern("answers:" + roomCode + ":*");
        deleteByPattern("ready:" + roomCode + ":*");
        deleteByPattern("readied:" + roomCode + ":*");
        deleteByPattern("roundstartedat:" + roomCode + ":*");
        redisTemplate.delete("previews:" + roomCode);
        redisTemplate.delete("joinack:" + roomCode);
        deleteByPattern("roundstarted:" + roomCode + ":*");
        deleteByPattern("roundclosed:" + roomCode + ":*");
        deleteByPattern("correctIndex:" + roomCode + ":*");
        deleteByPattern("disconnect:" + roomCode + ":*");
    }

    private void deleteByPattern(String pattern) {
        org.springframework.data.redis.core.ScanOptions options =
            org.springframework.data.redis.core.ScanOptions.scanOptions().match(pattern).count(100).build();
        try (var cursor = redisTemplate.scan(options)) {
            cursor.forEachRemaining(redisTemplate::delete);
        }
    }
}
