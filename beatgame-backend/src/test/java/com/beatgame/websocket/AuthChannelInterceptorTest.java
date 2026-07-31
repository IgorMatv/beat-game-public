package com.beatgame.websocket;

import com.beatgame.auth.AuthClaims;
import com.beatgame.auth.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthChannelInterceptorTest {

    @Mock JwtService jwtService;
    @Mock MessageChannel channel;

    AuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new AuthChannelInterceptor(jwtService);
    }

    private Message<byte[]> connectMessage(String jwt, Map<String, Object> sessionAttrs) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionAttributes(sessionAttrs);
        if (jwt != null) accessor.addNativeHeader("playerToken", jwt);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> subscribeMessage(String destination, Map<String, Object> sessionAttrs) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionAttributes(sessionAttrs);
        accessor.setDestination(destination);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void connect_validToken_populatesSessionAttributesFromClaims() {
        Map<String, Object> sessionAttrs = new HashMap<>();
        when(jwtService.verify("valid-jwt")).thenReturn(new AuthClaims("player-uuid", "ABC123"));

        interceptor.preSend(connectMessage("valid-jwt", sessionAttrs), channel);

        assertThat(sessionAttrs)
            .containsEntry("playerToken", "player-uuid")
            .containsEntry("roomCode", "ABC123");
    }

    @Test
    void connect_invalidSignature_throwsStompAuthException() {
        Map<String, Object> sessionAttrs = new HashMap<>();
        when(jwtService.verify("bad-jwt")).thenThrow(new SignatureException("bad signature"));

        assertThatThrownBy(() -> interceptor.preSend(connectMessage("bad-jwt", sessionAttrs), channel))
            .isInstanceOf(StompAuthException.class);
        assertThat(sessionAttrs).isEmpty();
    }

    @Test
    void connect_expiredToken_throwsStompAuthException() {
        Map<String, Object> sessionAttrs = new HashMap<>();
        when(jwtService.verify("expired-jwt")).thenThrow(new ExpiredJwtException(null, null, "expired"));

        assertThatThrownBy(() -> interceptor.preSend(connectMessage("expired-jwt", sessionAttrs), channel))
            .isInstanceOf(StompAuthException.class);
    }

    @Test
    void subscribe_matchingRoomCode_isAllowedThrough() {
        Map<String, Object> sessionAttrs = new HashMap<>(Map.of("playerToken", "p1", "roomCode", "ABC123"));

        Message<?> result = interceptor.preSend(subscribeMessage("/topic/game.ABC123", sessionAttrs), channel);

        assertThat(result).isNotNull();
    }

    @Test
    void subscribe_foreignRoomCode_throwsStompAuthException() {
        Map<String, Object> sessionAttrs = new HashMap<>(Map.of("playerToken", "p1", "roomCode", "ABC123"));

        assertThatThrownBy(() ->
            interceptor.preSend(subscribeMessage("/topic/game.XYZ999", sessionAttrs), channel))
            .isInstanceOf(StompAuthException.class);
    }
}
