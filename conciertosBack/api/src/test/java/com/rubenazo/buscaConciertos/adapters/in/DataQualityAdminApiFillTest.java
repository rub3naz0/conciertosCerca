package com.rubenazo.buscaConciertos.adapters.in;

import com.rubenazo.buscaConciertos.application.ports.in.DataQualityInputPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DataQualityAdminApiFillTest {

    @Mock private DataQualityInputPort dataQualityInputPort;
    @Mock private ApplicationEventPublisher eventPublisher;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DataQualityAdminApi api = new DataQualityAdminApi(dataQualityInputPort, eventPublisher);
        mockMvc = MockMvcBuilders.standaloneSetup(api).build();
    }

    // --- 200: valid fill ---

    @Test
    void fill_returns200OnSuccess() throws Exception {
        doNothing().when(dataQualityInputPort).fill(7L, "Calle Mayor 5");

        mockMvc.perform(post("/api/admin/quality/7/fill")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"Calle Mayor 5\"}"))
            .andExpect(status().isOk());

        verify(dataQualityInputPort).fill(7L, "Calle Mayor 5");
    }

    // --- 400: blank value propagated from use case ---

    @Test
    void fill_returns400WhenValueIsBlank() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "value must not be blank"))
            .when(dataQualityInputPort).fill(7L, "   ");

        mockMvc.perform(post("/api/admin/quality/7/fill")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"   \"}"))
            .andExpect(status().isBadRequest());
    }

    // --- 400: non-numeric lat propagated from use case ---

    @Test
    void fill_returns400WhenLatNonNumeric() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "lat/lng must be numeric"))
            .when(dataQualityInputPort).fill(12L, "not-a-number");

        mockMvc.perform(post("/api/admin/quality/12/fill")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"not-a-number\"}"))
            .andExpect(status().isBadRequest());
    }

    // --- 404: unknown id ---

    @Test
    void fill_returns404WhenIdNotFound() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"))
            .when(dataQualityInputPort).fill(9999L, "value");

        mockMvc.perform(post("/api/admin/quality/9999/fill")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"value\"}"))
            .andExpect(status().isNotFound());
    }

    // --- 409: terminal status (approved) ---

    @Test
    void fill_returns409WhenTerminalStatus() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Already approved"))
            .when(dataQualityInputPort).fill(1L, "value");

        mockMvc.perform(post("/api/admin/quality/1/fill")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"value\"}"))
            .andExpect(status().isConflict());
    }

    // --- 409: auto_approved status ---

    @Test
    void fill_returns409WhenStatusIsAutoApproved() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Already auto_approved"))
            .when(dataQualityInputPort).fill(2L, "value");

        mockMvc.perform(post("/api/admin/quality/2/fill")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"value\"}"))
            .andExpect(status().isConflict());
    }

    // --- 422: concert entity ---

    @Test
    void fill_returns422WhenConcertEntity() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Concert fields are not manually fillable"))
            .when(dataQualityInputPort).fill(20L, "sala-foo");

        mockMvc.perform(post("/api/admin/quality/20/fill")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"sala-foo\"}"))
            .andExpect(status().isUnprocessableEntity());
    }

    // --- 422: name field ---

    @Test
    void fill_returns422WhenNameField() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "name is not manually fillable"))
            .when(dataQualityInputPort).fill(30L, "Sala Apolo");

        mockMvc.perform(post("/api/admin/quality/30/fill")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"Sala Apolo\"}"))
            .andExpect(status().isUnprocessableEntity());
    }
}
