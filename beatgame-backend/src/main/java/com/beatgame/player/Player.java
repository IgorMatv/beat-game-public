package com.beatgame.player;

import com.beatgame.room.Room;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "players")
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false, length = 50)
    private String name;

    private int score = 0;

    @Column(name = "is_host")
    private boolean host = false;

    @Column(name = "player_token", nullable = false, unique = true, length = 64)
    private String playerToken;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt = LocalDateTime.now();

    public Player() {}

    public Long getId() { return id; }
    public Room getRoom() { return room; }
    public void setRoom(Room room) { this.room = room; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public boolean isHost() { return host; }
    public void setHost(boolean host) { this.host = host; }
    public String getPlayerToken() { return playerToken; }
    public void setPlayerToken(String playerToken) { this.playerToken = playerToken; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
}
