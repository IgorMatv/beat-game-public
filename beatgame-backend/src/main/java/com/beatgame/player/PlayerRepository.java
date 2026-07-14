package com.beatgame.player;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    List<Player> findByRoomId(Long roomId);

    Optional<Player> findByPlayerToken(String playerToken);

    long countByRoomId(Long roomId);
}
