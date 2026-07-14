package com.beatgame.game;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GameSessionRepository extends JpaRepository<GameSession, Long> {

    Optional<GameSession> findTopByRoomIdOrderByStartedAtDesc(Long roomId);
}
