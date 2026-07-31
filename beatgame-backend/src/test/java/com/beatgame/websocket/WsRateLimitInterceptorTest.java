package com.beatgame.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class WsRateLimitInterceptorTest {

    @Mock MessageChannel channel;

    WsRateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new WsRateLimitInterceptor();
    }

    private Message<byte[]> sendMessage(String destination, String playerToken) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination(destination);
        Map<String, Object> sessionAttrs = new HashMap<>();
        if (playerToken != null) sessionAttrs.put("playerToken", playerToken);
        accessor.setSessionAttributes(sessionAttrs);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void allowsMessagesWithinTheLimit() {
        for (int i = 0; i < 20; i++) {
            Message<?> result = interceptor.preSend(sendMessage("/app/game.answer", "player-1"), channel);
            assertThat(result).isNotNull();
        }
    }

    @Test
    void dropsMessagesOnceTheLimitIsExceeded() {
        for (int i = 0; i < 20; i++) {
            interceptor.preSend(sendMessage("/app/game.answer", "player-1"), channel);
        }

        Message<?> result = interceptor.preSend(sendMessage("/app/game.answer", "player-1"), channel);

        assertThat(result).isNull();
    }

    @Test
    void tracksEachPlayerIndependently() {
        for (int i = 0; i < 20; i++) {
            interceptor.preSend(sendMessage("/app/game.answer", "player-1"), channel);
        }

        Message<?> result = interceptor.preSend(sendMessage("/app/game.answer", "player-2"), channel);

        assertThat(result).isNotNull();
    }

    @Test
    void limitsGameReadyTheSameAsGameAnswer() {
        for (int i = 0; i < 20; i++) {
            interceptor.preSend(sendMessage("/app/game.ready", "player-1"), channel);
        }

        Message<?> result = interceptor.preSend(sendMessage("/app/game.ready", "player-1"), channel);

        assertThat(result).isNull();
    }

    @Test
    void neverLimitsUnrelatedDestinations() {
        for (int i = 0; i < 50; i++) {
            Message<?> result = interceptor.preSend(sendMessage("/app/game.start", "player-1"), channel);
            assertThat(result).isNotNull();
        }
    }

    @Test
    void passesThroughWhenSessionHasNoPlayerToken() {
        Message<?> result = interceptor.preSend(sendMessage("/app/game.answer", null), channel);

        assertThat(result).isNotNull();
    }

    @Test
    void ignoresNonSendCommands() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/app/game.answer");
        accessor.setSessionAttributes(new HashMap<>(Map.of("playerToken", "player-1")));
        accessor.setLeaveMutable(true);
        Message<byte[]> subscribeMessage = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(subscribeMessage, channel);

        assertThat(result).isNotNull();
    }
}
