package com.beatgame.player;

import com.beatgame.room.Room;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    @Mock PlayerRepository playerRepository;
    PlayerService playerService;

    @BeforeEach
    void setUp() {
        playerService = new PlayerService(playerRepository);
    }

    @Test
    void createPlayer_setsAllFields() {
        Room room = new Room();
        when(playerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Player player = playerService.createPlayer(room, "Alice", true);

        assertThat(player.getName()).isEqualTo("Alice");
        assertThat(player.isHost()).isTrue();
        assertThat(player.getRoom()).isSameAs(room);
        assertThat(player.getPlayerToken()).matches("[0-9a-f\\-]{36}");
    }

    @Test
    void createPlayer_guestIsNotHost() {
        Room room = new Room();
        when(playerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Player player = playerService.createPlayer(room, "Bob", false);

        assertThat(player.isHost()).isFalse();
    }

    @Test
    void createPlayer_tokenIsUnique() {
        Room room = new Room();
        when(playerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Player p1 = playerService.createPlayer(room, "A", false);
        Player p2 = playerService.createPlayer(room, "B", false);

        assertThat(p1.getPlayerToken()).isNotEqualTo(p2.getPlayerToken());
    }
}
