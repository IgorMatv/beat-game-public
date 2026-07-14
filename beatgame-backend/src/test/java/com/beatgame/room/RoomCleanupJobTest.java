package com.beatgame.room;

import com.beatgame.player.Player;
import com.beatgame.player.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomCleanupJobTest {

    @Mock RoomRepository roomRepository;
    @Mock PlayerRepository playerRepository;
    RoomCleanupJob job;

    @BeforeEach
    void setUp() {
        job = new RoomCleanupJob(roomRepository, playerRepository);
    }

    @Test
    void deleteStaleRooms_deletesPlayersBeforeRooms() {
        Room room = new Room();
        room.setCode("OLD123");
        ReflectionTestUtils.setField(room, "id", 1L);
        Player player = new Player();
        player.setName("Alice");
        when(roomRepository.findStaleRooms(any())).thenReturn(List.of(room));
        when(playerRepository.findByRoomId(1L)).thenReturn(List.of(player));

        job.deleteStaleRooms();

        verify(playerRepository).deleteAll(List.of(player));
        verify(roomRepository).deleteAll(List.of(room));
        verify(playerRepository).findByRoomId(1L);
    }

    @Test
    void deleteStaleRooms_doesNothing_whenNoStaleRooms() {
        when(roomRepository.findStaleRooms(any())).thenReturn(List.of());

        job.deleteStaleRooms();

        verify(playerRepository, never()).deleteAll(any());
        verify(roomRepository, never()).deleteAll(any());
    }

    @Test
    void deleteStaleRooms_deletesPlayersForEachRoom() {
        Room room1 = new Room(); room1.setCode("R1");
        ReflectionTestUtils.setField(room1, "id", 1L);
        Room room2 = new Room(); room2.setCode("R2");
        ReflectionTestUtils.setField(room2, "id", 2L);
        Player p1 = new Player(); p1.setName("A");
        Player p2 = new Player(); p2.setName("B");
        when(roomRepository.findStaleRooms(any())).thenReturn(List.of(room1, room2));
        when(playerRepository.findByRoomId(1L)).thenReturn(List.of(p1));
        when(playerRepository.findByRoomId(2L)).thenReturn(List.of(p2));

        job.deleteStaleRooms();

        verify(playerRepository).findByRoomId(1L);
        verify(playerRepository).findByRoomId(2L);
        verify(playerRepository).deleteAll(List.of(p1));
        verify(playerRepository).deleteAll(List.of(p2));
        verify(roomRepository).deleteAll(List.of(room1, room2));
    }
}
