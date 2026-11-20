package com.hotelbooking.keycard.controller;

import com.hotelbooking.keycard.db.KeycardEntity;
import com.hotelbooking.keycard.model.KeycardDto;
import com.hotelbooking.keycard.service.KeycardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tests the REST layer of /api/keycards. The service is mocked, no server or database is started. */
class KeycardControllerTest {

    private KeycardService keycardService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        keycardService = mock(KeycardService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new KeycardController(keycardService)).build();
    }

    @Test
    @DisplayName("48. POST /api/keycards returns 201 and the issued card")
    void create_returns201() throws Exception {
        when(keycardService.create(any(KeycardEntity.class)))
                .thenReturn(new KeycardDto(1L, 5L, "RFID-0001", null, true));

        mockMvc.perform(post("/api/keycards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"booking": {"bookingId": 5}, "cardUid": "RFID-0001", "isActive": true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.keycardId").value(1))
                .andExpect(jsonPath("$.bookingId").value(5))
                .andExpect(jsonPath("$.cardUid").value("RFID-0001"));

        ArgumentCaptor<KeycardEntity> sent = ArgumentCaptor.forClass(KeycardEntity.class);
        verify(keycardService).create(sent.capture());
        assertEquals("RFID-0001", sent.getValue().getCardUid());
        assertEquals(5L, (long) sent.getValue().getBooking().getBookingId());
    }

    @Test
    @DisplayName("49. DELETE /api/keycards/{id} for an unknown card returns 404")
    void delete_unknownKeycard_returns404() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Keycard not found: 99"))
                .when(keycardService).delete(99L);

        mockMvc.perform(delete("/api/keycards/99"))
                .andExpect(status().isNotFound());
    }
}
