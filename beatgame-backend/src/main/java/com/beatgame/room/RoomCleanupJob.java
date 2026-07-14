package com.beatgame.room;

import com.beatgame.player.PlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class RoomCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RoomCleanupJob.class);

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;

    public RoomCleanupJob(RoomRepository roomRepository, PlayerRepository playerRepository) {
        this.roomRepository = roomRepository;
        this.playerRepository = playerRepository;
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void deleteStaleRooms() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        List<Room> stale = roomRepository.findStaleRooms(cutoff);
        if (stale.isEmpty()) return;

        stale.forEach(room ->
            playerRepository.deleteAll(playerRepository.findByRoomId(room.getId())));
        roomRepository.deleteAll(stale);
        log.info("Cleanup: deleted {} stale rooms", stale.size());
    }
}
