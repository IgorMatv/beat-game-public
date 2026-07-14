package com.beatgame.websocket;

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

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
            MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
            if (sessionAttrs == null) {
                log.warn("STOMP CONNECT arrived with null session attributes — playerToken and roomCode will not be stored");
                return message;
            }
            storeHeader(accessor, sessionAttrs, "playerToken");
            storeHeader(accessor, sessionAttrs, "roomCode");
        }
        return message;
    }

    private void storeHeader(StompHeaderAccessor accessor, Map<String, Object> sessionAttrs, String headerName) {
        String value = accessor.getFirstNativeHeader(headerName);
        if (value != null && !value.isBlank()) {
            sessionAttrs.put(headerName, value);
            log.debug("Stored STOMP header '{}' in session attributes", headerName);
        }
    }
}
