package com.beatgame.room;

import com.beatgame.auth.AuthClaims;
import com.beatgame.auth.JwtService;
import com.beatgame.game.GameService;
import com.beatgame.room.dto.CreateRoomRequest;
import com.beatgame.room.dto.CreateRoomResponse;
import com.beatgame.room.dto.JoinRoomRequest;
import com.beatgame.room.dto.JoinRoomResponse;
import com.beatgame.room.dto.RoomInfoResponse;
import com.beatgame.websocket.dto.PlayerInfo;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;
    private final JwtService jwtService;

    @Autowired(required = false)
    private GameService gameService;

    public RoomController(RoomService roomService, JwtService jwtService) {
        this.roomService = roomService;
        this.jwtService = jwtService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateRoomResponse createRoom(@RequestBody CreateRoomRequest request) {
        return roomService.createRoom(request.playerName());
    }

    @PostMapping("/solo")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateRoomResponse createSoloRoom(@RequestBody CreateRoomRequest request) {
        return roomService.createSoloRoom(request.playerName());
    }

    @GetMapping("/{code}")
    public RoomInfoResponse getRoom(@PathVariable String code) {
        return roomService.getRoomInfo(code);
    }

    @GetMapping("/{code}/players")
    public List<PlayerInfo> getRoomPlayers(@PathVariable String code) {
        return roomService.getRoomPlayers(code);
    }

    // Any member of the room may trigger a reset, not just the host — either
    // player can click "Play Again" first and reset is idempotent (issue #35:
    // a guest who clicked first was previously left stranded waiting on the
    // host). The check here is room membership, not host status: the caller's
    // token must have been issued for this specific room.
    @PostMapping("/{code}/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetRoom(@PathVariable String code, @RequestHeader("X-Player-Token") String playerToken) {
        AuthClaims claims;
        try {
            claims = jwtService.verify(playerToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (!code.equals(claims.roomCode())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (gameService != null) {
            gameService.resetRoom(code);
        }
    }

    @PostMapping("/{code}/join")
    public JoinRoomResponse joinRoom(@PathVariable String code,
                                     @RequestBody JoinRoomRequest request) {
        JoinRoomResponse response = roomService.joinRoom(code, request.playerName());
        if (gameService != null) {
            gameService.broadcastRoomState(code);
        }
        return response;
    }
}
