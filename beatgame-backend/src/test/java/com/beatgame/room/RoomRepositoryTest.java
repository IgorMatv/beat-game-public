package com.beatgame.room;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class RoomRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    RoomRepository roomRepository;

    @BeforeEach
    void clearTable() {
        roomRepository.deleteAll();
    }

    private Room room(String code, RoomStatus status, LocalDateTime createdAt) {
        Room r = new Room();
        r.setCode(code);
        r.setStatus(status);
        ReflectionTestUtils.setField(r, "createdAt", createdAt);
        return r;
    }

    @Test
    void findStaleRooms_includesOldInGameRooms() {
        // issue #84: a room stuck IN_GAME for 24h+ is abandoned, not a real game
        roomRepository.save(room("OLDGM1", RoomStatus.IN_GAME, LocalDateTime.now().minusHours(48)));

        List<Room> result = roomRepository.findStaleRooms(LocalDateTime.now().minusHours(24));

        assertThat(result).extracting(Room::getCode).containsExactly("OLDGM1");
    }

    @Test
    void findStaleRooms_excludesRecentInGameRoom() {
        roomRepository.save(room("LIVEGM", RoomStatus.IN_GAME, LocalDateTime.now().minusMinutes(5)));

        List<Room> result = roomRepository.findStaleRooms(LocalDateTime.now().minusHours(24));

        assertThat(result).isEmpty();
    }

    @Test
    void findStaleRooms_stillIncludesOldWaitingAndFinishedRooms() {
        roomRepository.save(room("OLDWT1", RoomStatus.WAITING, LocalDateTime.now().minusHours(48)));
        roomRepository.save(room("OLDFN1", RoomStatus.FINISHED, LocalDateTime.now().minusHours(48)));

        List<Room> result = roomRepository.findStaleRooms(LocalDateTime.now().minusHours(24));

        assertThat(result).extracting(Room::getCode)
            .containsExactlyInAnyOrder("OLDWT1", "OLDFN1");
    }
}
