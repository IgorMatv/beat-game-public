package com.beatgame.websocket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.SimpleBrokerRegistration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketConfigTest {

    @Test
    void configureMessageBroker_setsStompHeartbeatOnSimpleBroker() {
        WebSocketConfig config = new WebSocketConfig(mock(AuthChannelInterceptor.class), mock(WsRateLimitInterceptor.class));
        MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);
        SimpleBrokerRegistration registration = mock(SimpleBrokerRegistration.class);
        when(registry.enableSimpleBroker("/topic")).thenReturn(registration);
        when(registration.setHeartbeatValue(any())).thenReturn(registration);

        config.configureMessageBroker(registry);

        verify(registration).setHeartbeatValue(new long[]{10000, 10000});
        verify(registration).setTaskScheduler(any());
    }
}
