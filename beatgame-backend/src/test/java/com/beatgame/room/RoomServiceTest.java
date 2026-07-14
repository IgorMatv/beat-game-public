package com.beatgame.room;

import com.beatgame.player.Player;
import com.beatgame.player.PlayerRepository;
import com.beatgame.player.PlayerService;
import com.beatgame.room.dto.CreateRoomResponse;
import com.beatgame.room.dto.JoinRoomResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock RoomRepository roomRepository;
    @Mock PlayerService playerService;
    @Mock PlayerRepository playerRepository;
    RoomService roomService;

    @BeforeEach
    void setUp() {
        roomService = new RoomService(roomRepository, playerService, playerRepository);
    }

    @Test
    void createRoom_codeMatchesFormat() {
        when(roomRepository.existsByCode(any())).thenReturn(false);
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(playerService.createPlayer(any(), any(), anyBoolean())).thenReturn(playerWithToken("tok"));

        CreateRoomResponse response = roomService.createRoom("Alice");

        assertThat(response.roomCode()).matches("[A-Z0-9]{6}");
    }

    @Test
    void createRoom_retriesOnCodeCollision() {
        when(roomRepository.existsByCode(any()))
            .thenReturn(true).thenReturn(true).thenReturn(false);
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(playerService.createPlayer(any(), any(), anyBoolean())).thenReturn(playerWithToken("tok"));

        roomService.createRoom("Alice");

        verify(roomRepository, times(3)).existsByCode(any());
    }

    @Test
    void createRoom_throwsWhenAllCodesCollide() {
        when(roomRepository.existsByCode(any())).thenReturn(true);

        assertThatThrownBy(() -> roomService.createRoom("Alice"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createRoom_setsMaxPlayers2() {
        when(roomRepository.existsByCode(any())).thenReturn(false);
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(playerService.createPlayer(any(), any(), anyBoolean())).thenReturn(playerWithToken("tok"));

        roomService.createRoom("Alice");

        verify(roomRepository).save(argThat(r -> r.getMaxPlayers() == 2));
    }

    @Test
    void createRoom_returnsHostPlayerToken() {
        when(roomRepository.existsByCode(any())).thenReturn(false);
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(playerService.createPlayer(any(), any(), anyBoolean())).thenReturn(playerWithToken("host-tok"));

        CreateRoomResponse response = roomService.createRoom("Alice");

        assertThat(response.playerToken()).isEqualTo("host-tok");
        verify(playerService).createPlayer(any(), eq("Alice"), eq(true));
    }

    @Test
    void createSoloRoom_setsMaxPlayers1() {
        when(roomRepository.existsByCode(any())).thenReturn(false);
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(playerService.createPlayer(any(), any(), anyBoolean())).thenReturn(playerWithToken("tok"));

        roomService.createSoloRoom("Alice");

        verify(roomRepository).save(argThat(r -> r.getMaxPlayers() == 1));
    }

    private Player playerWithToken(String token) {
        Player p = new Player();
        p.setPlayerToken(token);
        return p;
    }

    @Test
    void joinRoom_returnsGuestPlayerToken() {
        Room room = roomWithCode("ABC123");
        when(roomRepository.findByCode("ABC123")).thenReturn(Optional.of(room));
        when(playerRepository.countByRoomId(any())).thenReturn(1L);
        when(playerService.createPlayer(any(), any(), anyBoolean())).thenReturn(playerWithToken("guest-tok"));

        JoinRoomResponse response = roomService.joinRoom("ABC123", "Bob");

        assertThat(response.playerToken()).isEqualTo("guest-tok");
        verify(playerService).createPlayer(any(), eq("Bob"), eq(false));
    }

    @Test
    void joinRoom_throwsNotFound_whenRoomMissing() {
        when(roomRepository.findByCode("XXX")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.joinRoom("XXX", "Bob"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void joinRoom_throwsConflict_whenRoomFull() {
        Room room = roomWithCode("ABC123");
        when(roomRepository.findByCode("ABC123")).thenReturn(Optional.of(room));
        when(playerRepository.countByRoomId(any())).thenReturn(2L);

        assertThatThrownBy(() -> roomService.joinRoom("ABC123", "Bob"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void joinRoom_throwsConflict_whenRoomNotWaiting() {
        Room room = roomWithCode("ABC123");
        room.setStatus(RoomStatus.IN_GAME);
        when(roomRepository.findByCode("ABC123")).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.joinRoom("ABC123", "Bob"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
    }

    private Room roomWithCode(String code) {
        Room room = new Room();
        room.setCode(code);
        room.setMaxPlayers((short) 2);
        room.setStatus(RoomStatus.WAITING);
        return room;
    }
}
