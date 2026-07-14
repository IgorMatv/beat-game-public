package com.beatgame.player;

import com.beatgame.room.Room;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PlayerService {

    private final PlayerRepository playerRepository;

    public PlayerService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    public Player createPlayer(Room room, String name, boolean host) {
        Player player = new Player();
        player.setRoom(room);
        player.setName(name);
        player.setHost(host);
        player.setPlayerToken(UUID.randomUUID().toString());
        return playerRepository.save(player);
    }
}
