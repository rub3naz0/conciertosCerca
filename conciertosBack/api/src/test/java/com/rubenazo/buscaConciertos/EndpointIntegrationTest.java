package com.rubenazo.buscaConciertos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class EndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getArtists_returns200WithJsonOnFreshDb() throws Exception {
        mockMvc.perform(get("/api/v1/artists"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    void getSalasConcierto_returns200WithJsonOnFreshDb() throws Exception {
        mockMvc.perform(get("/api/v1/salas-concierto"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    void getConcerts_returns200WithJsonOnFreshDb() throws Exception {
        mockMvc.perform(get("/api/v1/concerts"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    void headArtists_isNot500OnFreshDb() throws Exception {
        mockMvc.perform(head("/api/v1/artists"))
            .andExpect(status().is2xxSuccessful());
    }

    @Test
    void headSalasConcierto_isNot500OnFreshDb() throws Exception {
        mockMvc.perform(head("/api/v1/salas-concierto"))
            .andExpect(status().is2xxSuccessful());
    }

    @Test
    void headConcerts_isNot500OnFreshDb() throws Exception {
        mockMvc.perform(head("/api/v1/concerts"))
            .andExpect(status().is2xxSuccessful());
    }

    @Test
    void getArtists_responseHasExpectedJsonStructure() throws Exception {
        mockMvc.perform(get("/api/v1/artists"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getConcerts_responseHasExpectedJsonStructure() throws Exception {
        mockMvc.perform(get("/api/v1/concerts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.deleted_ids").isArray());
    }
}
