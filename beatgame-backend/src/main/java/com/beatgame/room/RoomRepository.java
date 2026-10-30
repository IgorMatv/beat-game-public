package com.beatgame.room;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    Optional<Room> findByCode(String code);

    boolean existsByCode(String code);

    long countByStatusIn(List<RoomStatus> statuses);

    // IN_GAME is included too (issue #84): no legitimate round timer, ready-gate,
    // or disconnect-forfeit window runs anywhere near 24h, so an IN_GAME room
    // this old is abandoned — the backend died before GameOverMessage ever
    // fired, not a real game still in progress.
    @Query("""
        SELECT r FROM Room r
        WHERE r.status IN ('WAITING', 'FINISHED', 'IN_GAME')
          AND r.createdAt < :before
        """)
    List<Room> findStaleRooms(@Param("before") LocalDateTime before);
}
