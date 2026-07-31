package com.beatgame.websocket;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Per-player throttle on the highest-frequency game messages — a buggy or malicious
// client spamming game.answer/game.ready shouldn't be able to hammer Redis. Runs after
// AuthChannelInterceptor in the chain, so session attributes are already verified.
@Component
public class WsRateLimitInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WsRateLimitInterceptor.class);
    private static final Set<String> LIMITED_DESTINATIONS = Set.of("/app/game.answer", "/app/game.ready");

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
            MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.SEND.equals(accessor.getCommand())) return message;
        if (!LIMITED_DESTINATIONS.contains(accessor.getDestination())) return message;

        Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
        String playerToken = sessionAttrs == null ? null : (String) sessionAttrs.get("playerToken");
        if (playerToken == null) return message;

        if (buckets.size() >= 100_000) return message;
        Bucket bucket = buckets.computeIfAbsent(playerToken, k -> newBucket());
        if (!bucket.tryConsume(1)) {
            log.warn("Dropping {} from player {}: rate limit exceeded", accessor.getDestination(), playerToken);
            return null;
        }
        return message;
    }

    private Bucket newBucket() {
        return Bucket.builder()
            .addLimit(Bandwidth.builder()
                .capacity(20)
                .refillGreedy(20, Duration.ofMinutes(1))
                .build())
            .build();
    }
}
