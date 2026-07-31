package com.beatgame.room;

import com.beatgame.auth.AuthClaims;
import com.beatgame.auth.JwtService;
import com.beatgame.game.GameService;
import com.beatgame.room.dto.CreateRoomResponse;
import com.beatgame.room.dto.JoinRoomResponse;
import com.beatgame.room.dto.RoomInfoResponse;
import com.beatgame.websocket.dto.PlayerInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RoomController.class)
class RoomControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean RoomService roomService;
    @MockBean GameService gameService;
    @MockBean JwtService jwtService;

    @Test
    void createRoom_returns201WithRoomCodeAndPlayerToken() throws Exception {
        when(roomService.createRoom("Alice"))
            .thenReturn(new CreateRoomResponse("ABC123", "tok-uuid", 1L));

        mockMvc.perform(post("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"playerName\":\"Alice\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.roomCode").value("ABC123"))
            .andExpect(jsonPath("$.playerToken").value("tok-uuid"))
            .andExpect(jsonPath("$.playerId").value(1));
    }

    @Test
    void createSoloRoom_returns201WithRoomCode() throws Exception {
        when(roomService.createSoloRoom("Alice"))
            .thenReturn(new CreateRoomResponse("XY1234", "tok-solo", 1L));

        mockMvc.perform(post("/api/rooms/solo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"playerName\":\"Alice\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.roomCode").value("XY1234"))
            .andExpect(jsonPath("$.playerToken").value("tok-solo"));
    }

    @Test
    void getRoom_returnsRoomInfo() throws Exception {
        when(roomService.getRoomInfo("ABC123"))
            .thenReturn(new RoomInfoResponse("ABC123", "WAITING", 1, 2));

        mockMvc.perform(get("/api/rooms/ABC123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("ABC123"))
            .andExpect(jsonPath("$.status").value("WAITING"))
            .andExpect(jsonPath("$.playerCount").value(1))
            .andExpect(jsonPath("$.maxPlayers").value(2));
    }

    @Test
    void getRoom_returns404_whenNotFound() throws Exception {
        when(roomService.getRoomInfo("ZZZ999"))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        mockMvc.perform(get("/api/rooms/ZZZ999"))
            .andExpect(status().isNotFound());
    }

    @Test
    void resetRoom_validTokenForThisRoom_returns204AndResetsGame() throws Exception {
        when(jwtService.verify("guest-tok")).thenReturn(new AuthClaims("guest-tok", "ABC123"));

        mockMvc.perform(post("/api/rooms/ABC123/reset")
                .header("X-Player-Token", "guest-tok"))
            .andExpect(status().isNoContent());

        verify(gameService).resetRoom("ABC123");
    }

    @Test
    void resetRoom_tokenForDifferentRoom_returns403AndDoesNotReset() throws Exception {
        when(jwtService.verify("other-room-tok")).thenReturn(new AuthClaims("other-room-tok", "ZZZ999"));

        mockMvc.perform(post("/api/rooms/ABC123/reset")
                .header("X-Player-Token", "other-room-tok"))
            .andExpect(status().isForbidden());

        verify(gameService, never()).resetRoom(any());
    }

    @Test
    void resetRoom_invalidToken_returns403AndDoesNotReset() throws Exception {
        when(jwtService.verify("bad-tok")).thenThrow(new io.jsonwebtoken.security.SignatureException("bad signature"));

        mockMvc.perform(post("/api/rooms/ABC123/reset")
                .header("X-Player-Token", "bad-tok"))
            .andExpect(status().isForbidden());

        verify(gameService, never()).resetRoom(any());
    }

    @Test
    void resetRoom_returns429AfterExceedingRateLimit() throws Exception {
        when(jwtService.verify(any())).thenReturn(new AuthClaims("tok", "ABC123"));

        // Use a distinct IP so this test's bucket is isolated from other tests
        String ip = "10.0.1.3";
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/rooms/ABC123/reset")
                    .header("X-Forwarded-For", ip)
                    .header("X-Player-Token", "tok"))
                .andExpect(status().isNoContent());
        }

        // 11th request exceeds the 10/min reset bucket
        mockMvc.perform(post("/api/rooms/ABC123/reset")
                .header("X-Forwarded-For", ip)
                .header("X-Player-Token", "tok"))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void joinRoom_returnsPlayerToken() throws Exception {
        when(roomService.joinRoom("ABC123", "Bob"))
            .thenReturn(new JoinRoomResponse("guest-tok", 2L, List.of(
                new PlayerInfo(1L, "Alice", true),
                new PlayerInfo(2L, "Bob", false)
            )));

        mockMvc.perform(post("/api/rooms/ABC123/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"playerName\":\"Bob\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.playerToken").value("guest-tok"))
            .andExpect(jsonPath("$.playerId").value(2));
    }

    @Test
    void joinRoom_returns409_whenRoomFull() throws Exception {
        when(roomService.joinRoom("ABC123", "Bob"))
            .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Room is full"));

        mockMvc.perform(post("/api/rooms/ABC123/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"playerName\":\"Bob\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void joinRoom_returns404_whenRoomNotFound() throws Exception {
        when(roomService.joinRoom("ZZZ", "Bob"))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        mockMvc.perform(post("/api/rooms/ZZZ/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"playerName\":\"Bob\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void createRoom_returns429AfterExceedingRateLimit() throws Exception {
        when(roomService.createRoom(any())).thenReturn(new CreateRoomResponse("X", "tok", 1L));

        // Use a distinct IP so this test's bucket is isolated from other tests
        String ip = "10.0.1.1";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/rooms")
                    .header("X-Forwarded-For", ip)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"playerName\":\"Test\"}"))
                .andExpect(status().isCreated());
        }

        // 6th request exceeds the 5/min create bucket
        mockMvc.perform(post("/api/rooms")
                .header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"playerName\":\"Test\"}"))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void joinRoom_returns429AfterExceedingRateLimit() throws Exception {
        when(roomService.joinRoom(any(), any()))
            .thenReturn(new JoinRoomResponse("tok", 1L, List.of()));

        // Use a distinct IP so this test's bucket is isolated from other tests
        String ip = "10.0.1.2";
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/rooms/ABC123/join")
                    .header("X-Forwarded-For", ip)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"playerName\":\"Test\"}"))
                .andExpect(status().isOk());
        }

        // 11th request exceeds the 10/min join bucket
        mockMvc.perform(post("/api/rooms/ABC123/join")
                .header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"playerName\":\"Test\"}"))
            .andExpect(status().isTooManyRequests());
    }
}
