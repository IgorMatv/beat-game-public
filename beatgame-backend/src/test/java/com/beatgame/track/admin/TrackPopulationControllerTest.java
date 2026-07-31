package com.beatgame.track.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TrackPopulationController.class)
class TrackPopulationControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean TrackPopulationService populationService;

    @Test
    void populate_returns429AfterExceedingRateLimit() throws Exception {
        when(populationService.populate("DEEZER", "GENRE", "POP", 4, 0))
            .thenReturn(new PopulationResult("DEEZER", "GENRE", "POP", 4, 0, 0, 0, 0));

        // Use a distinct IP so this test's bucket is isolated from other tests
        String ip = "10.0.1.4";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/admin/tracks/populate")
                    .header("X-Forwarded-For", ip)
                    .header("X-Admin-Token", "12345")
                    .param("provider", "DEEZER")
                    .param("categoryType", "GENRE")
                    .param("category", "POP"))
                .andExpect(status().isOk());
        }

        // 4th request exceeds the 3/min admin populate bucket
        mockMvc.perform(post("/api/admin/tracks/populate")
                .header("X-Forwarded-For", ip)
                .header("X-Admin-Token", "12345")
                .param("provider", "DEEZER")
                .param("categoryType", "GENRE")
                .param("category", "POP"))
            .andExpect(status().isTooManyRequests());
    }
}
