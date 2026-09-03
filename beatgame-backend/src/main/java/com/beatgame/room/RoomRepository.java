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

    @Query("""
        SELECT r FROM Room r
        WHERE r.status IN ('WAITING', 'FINISHED')
          AND r.createdAt < :before
        """)
    List<Room> findStaleRooms(@Param("before") LocalDateTime before);
}
