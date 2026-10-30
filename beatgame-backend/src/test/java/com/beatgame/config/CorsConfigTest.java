package com.beatgame.config;

import com.beatgame.track.CatalogController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CatalogController.class)
@Import(CorsConfig.class)
class CorsConfigTest {

    @Autowired MockMvc mockMvc;

    @Test
    void allowedOrigin_getsAccessControlAllowOriginHeader() throws Exception {
        mockMvc.perform(get("/api/genres").header("Origin", "http://localhost:5173"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void disallowedOrigin_isRejected() throws Exception {
        mockMvc.perform(get("/api/genres").header("Origin", "https://evil.example.com"))
            .andExpect(status().isForbidden());
    }
}
