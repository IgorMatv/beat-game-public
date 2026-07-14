package com.beatgame.room;

import com.beatgame.game.GameService;
import com.beatgame.room.dto.CreateRoomRequest;
import com.beatgame.room.dto.CreateRoomResponse;
import com.beatgame.room.dto.JoinRoomRequest;
import com.beatgame.room.dto.JoinRoomResponse;
import com.beatgame.room.dto.RoomInfoResponse;
import com.beatgame.websocket.dto.PlayerInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    @Autowired(required = false)
    private GameService gameService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
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

    @PostMapping("/{code}/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetRoom(@PathVariable String code) {
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
