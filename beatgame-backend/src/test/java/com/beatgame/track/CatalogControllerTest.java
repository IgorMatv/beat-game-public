package com.beatgame.track;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CatalogController.class)
class CatalogControllerTest {

    @Autowired MockMvc mockMvc;

    @Test
    void getGenres_returnsAllGenreNames() throws Exception {
        mockMvc.perform(get("/api/genres"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(Genre.values().length))
            .andExpect(jsonPath("$[0]").value("POP"));
    }

    @Test
    void getDecades_returnsAllDecadeStartYears() throws Exception {
        mockMvc.perform(get("/api/decades"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(Decade.values().length))
            .andExpect(jsonPath("$[0]").value(1980));
    }
}
