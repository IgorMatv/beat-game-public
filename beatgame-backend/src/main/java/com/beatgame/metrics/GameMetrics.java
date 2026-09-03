package com.beatgame.metrics;

import com.beatgame.room.RoomRepository;
import com.beatgame.room.RoomStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class GameMetrics {

    private static final List<RoomStatus> ACTIVE_STATUSES = List.of(RoomStatus.WAITING, RoomStatus.IN_GAME);

    private final AtomicInteger connectedPlayers = new AtomicInteger();
    private final MeterRegistry registry;
    private final Counter gamesStarted;
    private final Counter reconnects;

    public GameMetrics(MeterRegistry registry, RoomRepository roomRepository) {
        this.registry = registry;
        Gauge.builder("beatgame_rooms_active", roomRepository,
                repo -> repo.countByStatusIn(ACTIVE_STATUSES))
            .register(registry);
        Gauge.builder("beatgame_players_connected", connectedPlayers, AtomicInteger::get)
            .register(registry);
        this.gamesStarted = Counter.builder("beatgame_games_started_total").register(registry);
        this.reconnects = Counter.builder("beatgame_reconnects_total").register(registry);
    }

    @EventListener
    public void onSessionConnected(SessionConnectedEvent event) {
        connectedPlayers.incrementAndGet();
    }

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        connectedPlayers.decrementAndGet();
    }

    public void incrementGamesCompleted(String outcome) {
        registry.counter("beatgame_games_completed_total", "outcome", outcome).increment();
    }

    public void incrementGamesStarted() {
        gamesStarted.increment();
    }

    public void incrementReconnects() {
        reconnects.increment();
    }
}
