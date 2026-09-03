package com.beatgame.metrics;

import com.beatgame.room.RoomRepository;
import com.beatgame.room.RoomStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameMetricsTest {

    @Mock RoomRepository roomRepository;
    SimpleMeterRegistry registry;
    GameMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new GameMetrics(registry, roomRepository);
    }

    @Test
    void roomsActiveGauge_reflectsWaitingAndInGameRoomCount() {
        when(roomRepository.countByStatusIn(List.of(RoomStatus.WAITING, RoomStatus.IN_GAME))).thenReturn(3L);

        double value = registry.get("beatgame_rooms_active").gauge().value();

        assertThat(value).isEqualTo(3.0);
    }

    @Test
    void playersConnectedGauge_incrementsOnSessionConnected() {
        metrics.onSessionConnected(mock(SessionConnectedEvent.class));

        double value = registry.get("beatgame_players_connected").gauge().value();

        assertThat(value).isEqualTo(1.0);
    }

    @Test
    void playersConnectedGauge_decrementsOnSessionDisconnect() {
        metrics.onSessionConnected(mock(SessionConnectedEvent.class));

        metrics.onSessionDisconnect(mock(SessionDisconnectEvent.class));

        double value = registry.get("beatgame_players_connected").gauge().value();

        assertThat(value).isEqualTo(0.0);
    }

    @Test
    void incrementGamesCompleted_incrementsCounterForThatOutcome() {
        metrics.incrementGamesCompleted("finished");
        metrics.incrementGamesCompleted("finished");

        assertThat(registry.get("beatgame_games_completed_total").tag("outcome", "finished").counter().count())
            .isEqualTo(2.0);
    }

    @Test
    void incrementGamesCompleted_tracksDifferentOutcomesSeparately() {
        metrics.incrementGamesCompleted("finished");
        metrics.incrementGamesCompleted("forfeited");
        metrics.incrementGamesCompleted("forfeited");

        assertThat(registry.get("beatgame_games_completed_total").tag("outcome", "finished").counter().count())
            .isEqualTo(1.0);
        assertThat(registry.get("beatgame_games_completed_total").tag("outcome", "forfeited").counter().count())
            .isEqualTo(2.0);
    }

    @Test
    void incrementGamesStarted_incrementsCounter() {
        metrics.incrementGamesStarted();
        metrics.incrementGamesStarted();
        metrics.incrementGamesStarted();

        assertThat(registry.get("beatgame_games_started_total").counter().count()).isEqualTo(3.0);
    }

    @Test
    void incrementReconnects_incrementsCounter() {
        metrics.incrementReconnects();

        assertThat(registry.get("beatgame_reconnects_total").counter().count()).isEqualTo(1.0);
    }
}
