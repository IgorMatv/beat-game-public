package com.beatgame.websocket;

import com.beatgame.auth.AuthClaims;
import com.beatgame.auth.JwtService;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthChannelInterceptor.class);

    private final JwtService jwtService;

    public AuthChannelInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
            MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            handleConnect(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            handleSubscribe(accessor);
        }
        return message;
    }

    private void handleConnect(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
        if (sessionAttrs == null) {
            log.warn("STOMP CONNECT arrived with null session attributes — rejecting");
            throw new StompAuthException("Invalid session");
        }

        String jwt = accessor.getFirstNativeHeader("playerToken");
        AuthClaims claims;
        try {
            claims = jwtService.verify(jwt);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Rejected CONNECT: invalid or expired token ({})", e.getMessage());
            throw new StompAuthException("Invalid or expired session — please rejoin");
        }

        sessionAttrs.put("playerToken", claims.playerToken());
        sessionAttrs.put("roomCode", claims.roomCode());
        log.debug("Verified CONNECT for room {}", claims.roomCode());
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
        String sessionRoomCode = sessionAttrs == null ? null : (String) sessionAttrs.get("roomCode");

        if (destination == null || !destinationRoomCodeMatches(destination, sessionRoomCode)) {
            log.warn("Rejected SUBSCRIBE to {}: does not match session room {}", destination, sessionRoomCode);
            throw new StompAuthException("Not authorized for this room");
        }
    }

    private boolean destinationRoomCodeMatches(String destination, String sessionRoomCode) {
        if (sessionRoomCode == null) return false;
        int dot = destination.lastIndexOf('.');
        if (dot < 0) return false;
        String destinationRoomCode = destination.substring(dot + 1);
        return sessionRoomCode.equals(destinationRoomCode);
    }
}
