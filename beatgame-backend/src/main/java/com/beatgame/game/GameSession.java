package com.beatgame.game;

import com.beatgame.room.Room;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Entity
@Table(name = "game_sessions")
public class GameSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "track_ids", columnDefinition = "bigint[]")
    private Long[] trackIds = new Long[0];

    @Column(name = "current_round")
    private short currentRound = 0;

    @Column(name = "started_at")
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    public GameSession() {}

    public Long getId() { return id; }
    public Room getRoom() { return room; }
    public void setRoom(Room room) { this.room = room; }
    public Long[] getTrackIds() { return trackIds; }
    public void setTrackIds(Long[] trackIds) { this.trackIds = trackIds; }
    public short getCurrentRound() { return currentRound; }
    public void setCurrentRound(short currentRound) { this.currentRound = currentRound; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
}
