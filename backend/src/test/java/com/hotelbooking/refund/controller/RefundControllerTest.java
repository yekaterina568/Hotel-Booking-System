package com.hotelbooking.refund.controller;

import com.hotelbooking.refund.model.RefundDto;
import com.hotelbooking.refund.model.RefundState;
import com.hotelbooking.refund.service.RefundService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tests the REST layer of /api/refunds. The service is mocked, no server or database is started. */
class RefundControllerTest {

    private RefundService refundService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        refundService = mock(RefundService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new RefundController(refundService)).build();
    }

    @Test
    @DisplayName("50. GET /api/refunds returns the list of refunds as JSON")
    void getAll_returnsJsonList() throws Exception {
        when(refundService.findAll()).thenReturn(List.of(
                new RefundDto(1L, 5L, new BigDecimal("15000.00"), "Guest cancelled", null, RefundState.COMPLETED)));

        mockMvc.perform(get("/api/refunds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].bookingId").value(5))
                .andExpect(jsonPath("$[0].refundReason").value("Guest cancelled"))
                .andExpect(jsonPath("$[0].state").value("COMPLETED"));
    }

    @Test
    @DisplayName("51. GET /api/refunds/{id} for an unknown refund returns 404")
    void getById_unknownRefund_returns404() throws Exception {
        when(refundService.findById(404L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Refund not found: 404"));

        mockMvc.perform(get("/api/refunds/404"))
                .andExpect(status().isNotFound());
    }
}
