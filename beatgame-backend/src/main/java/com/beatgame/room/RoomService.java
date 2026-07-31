package com.beatgame.room;

import com.beatgame.auth.JwtService;
import com.beatgame.player.Player;
import com.beatgame.player.PlayerRepository;
import com.beatgame.player.PlayerService;
import com.beatgame.room.dto.CreateRoomResponse;
import com.beatgame.room.dto.JoinRoomResponse;
import com.beatgame.room.dto.RoomInfoResponse;
import com.beatgame.websocket.dto.PlayerInfo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.List;

@Service
@Transactional
public class RoomService {

    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 6;
    private static final int MAX_RETRIES = 10;
    private static final int MAX_PLAYER_NAME_LENGTH = 16; // matches the frontend input's maxLength

    private final RoomRepository roomRepository;
    private final PlayerService playerService;
    private final PlayerRepository playerRepository;
    private final JwtService jwtService;
    private final SecureRandom random = new SecureRandom();

    public RoomService(RoomRepository roomRepository,
                       PlayerService playerService,
                       PlayerRepository playerRepository,
                       JwtService jwtService) {
        this.roomRepository = roomRepository;
        this.playerService = playerService;
        this.playerRepository = playerRepository;
        this.jwtService = jwtService;
    }

    public CreateRoomResponse createRoom(String playerName) {
        validatePlayerName(playerName);
        Room room = buildRoom((short) 2);
        roomRepository.save(room);
        Player host = playerService.createPlayer(room, playerName, true);
        return new CreateRoomResponse(room.getCode(), jwtService.issue(host.getPlayerToken(), room.getCode()), host.getId());
    }

    public CreateRoomResponse createSoloRoom(String playerName) {
        validatePlayerName(playerName);
        Room room = buildRoom((short) 1);
        roomRepository.save(room);
        Player player = playerService.createPlayer(room, playerName, true);
        return new CreateRoomResponse(room.getCode(), jwtService.issue(player.getPlayerToken(), room.getCode()), player.getId());
    }

    public JoinRoomResponse joinRoom(String code, String playerName) {
        validatePlayerName(playerName);
        Room room = roomRepository.findByCode(code)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        if (room.getStatus() != RoomStatus.WAITING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room is not accepting players");
        }

        long count = playerRepository.countByRoomId(room.getId());
        if (count >= room.getMaxPlayers()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room is full");
        }

        Player player = playerService.createPlayer(room, playerName, false);
        List<PlayerInfo> players = playerRepository.findByRoomId(room.getId()).stream()
                .map(p -> new PlayerInfo(p.getId(), p.getName(), p.isHost()))
                .toList();
        return new JoinRoomResponse(jwtService.issue(player.getPlayerToken(), room.getCode()), player.getId(), players);
    }

    @Transactional(readOnly = true)
    public List<PlayerInfo> getRoomPlayers(String code) {
        Room room = roomRepository.findByCode(code)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
        return playerRepository.findByRoomId(room.getId()).stream()
            .map(p -> new PlayerInfo(p.getId(), p.getName(), p.isHost()))
            .toList();
    }

    @Transactional(readOnly = true)
    public RoomInfoResponse getRoomInfo(String code) {
        Room room = roomRepository.findByCode(code)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
        long count = playerRepository.countByRoomId(room.getId());
        return new RoomInfoResponse(room.getCode(), room.getStatus().name(), (int) count, room.getMaxPlayers());
    }

    private void validatePlayerName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Player name must not be blank");
        }
        if (playerName.length() > MAX_PLAYER_NAME_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Player name must be at most " + MAX_PLAYER_NAME_LENGTH + " characters");
        }
    }

    private Room buildRoom(short maxPlayers) {
        Room room = new Room();
        room.setCode(generateUniqueCode());
        room.setStatus(RoomStatus.WAITING);
        room.setMaxPlayers(maxPlayers);
        return room;
    }

    private String generateUniqueCode() {
        for (int i = 0; i < MAX_RETRIES; i++) {
            String code = randomCode();
            if (!roomRepository.existsByCode(code)) return code;
        }
        throw new IllegalStateException("Could not generate unique room code after " + MAX_RETRIES + " attempts");
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
