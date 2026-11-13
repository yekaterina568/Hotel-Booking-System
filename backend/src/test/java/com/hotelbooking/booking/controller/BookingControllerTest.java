package com.hotelbooking.booking.controller;

import com.hotelbooking.booking.db.BookingEntity;
import com.hotelbooking.booking.model.BookingDto;
import com.hotelbooking.booking.model.BookingState;
import com.hotelbooking.booking.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests the REST layer of /api/bookings: URLs, HTTP status codes and JSON.
 * The service is mocked and MockMvc runs without starting the server or the database.
 */
class BookingControllerTest {

    private static final OffsetDateTime ARRIVAL = OffsetDateTime.parse("2026-11-20T14:00:00+05:00");

    private BookingService bookingService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        bookingService = mock(BookingService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new BookingController(bookingService)).build();
    }

    private BookingDto dto(long id, BookingState state) {
        return new BookingDto(id, 1L, 10L, ARRIVAL, state, null);
    }

    @Test
    @DisplayName("17. GET /api/bookings returns the list as JSON")
    void getAll_returnsJsonList() throws Exception {
        when(bookingService.findAll()).thenReturn(List.of(dto(1L, BookingState.CONFIRMED)));

        mockMvc.perform(get("/api/bookings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].bookingId").value(1))
                .andExpect(jsonPath("$[0].guestId").value(1))
                .andExpect(jsonPath("$[0].roomId").value(10))
                .andExpect(jsonPath("$[0].state").value("CONFIRMED"));
    }

    @Test
    @DisplayName("18. POST /api/bookings returns 201 and passes guest and room to the service")
    void create_returns201() throws Exception {
        when(bookingService.create(any(BookingEntity.class))).thenReturn(dto(42L, BookingState.CONFIRMED));

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"guest": {"guestId": 1}, "room": {"roomId": 10},
                                 "expectedArrival": "2026-11-20T14:00:00+05:00"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").value(42))
                .andExpect(jsonPath("$.state").value("CONFIRMED"));

        ArgumentCaptor<BookingEntity> sent = ArgumentCaptor.forClass(BookingEntity.class);
        verify(bookingService).create(sent.capture());
        assertEquals(1L, (long) sent.getValue().getGuest().getGuestId());
        assertEquals(10L, (long) sent.getValue().getRoom().getRoomId());
    }

    @Test
    @DisplayName("19. POST /api/bookings/{id}/check-in for an unknown booking returns 404")
    void checkIn_unknownBooking_returns404() throws Exception {
        when(bookingService.checkIn(999L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found: 999"));

        mockMvc.perform(post("/api/bookings/999/check-in"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("20. POST /api/bookings/{id}/cancel for a checked-in booking returns 409")
    void cancel_checkedInBooking_returns409() throws Exception {
        when(bookingService.cancel(5L))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Only a confirmed booking can be cancelled"));

        mockMvc.perform(post("/api/bookings/5/cancel"))
                .andExpect(status().isConflict());
    }
}
