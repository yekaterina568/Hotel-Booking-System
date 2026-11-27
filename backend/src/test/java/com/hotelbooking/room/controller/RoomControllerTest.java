package com.hotelbooking.room.controller;

import com.hotelbooking.booking.service.AvailabilityService;
import com.hotelbooking.room.model.CleaningState;
import com.hotelbooking.room.model.RoomDto;
import com.hotelbooking.room.model.RoomType;
import com.hotelbooking.room.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tests the REST layer of /api/rooms. Services are mocked, no server or database is started. */
class RoomControllerTest {

    private RoomService roomService;
    private AvailabilityService availabilityService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        roomService = mock(RoomService.class);
        availabilityService = mock(AvailabilityService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new RoomController(roomService, availabilityService)).build();
    }

    @Test
    @DisplayName("52. GET /api/rooms/available passes dates and room type to the search and returns free rooms")
    void getAvailable_passesDatesAndType() throws Exception {
        when(availabilityService.findAvailableRooms(any(), any(), eq(RoomType.SUITE))).thenReturn(List.of(
                new RoomDto(3L, 305, RoomType.SUITE, CleaningState.CLEANED, new BigDecimal("50000.00"), 1L)));

        mockMvc.perform(get("/api/rooms/available")
                        .param("from", "2026-11-20T09:00:00Z")
                        .param("to", "2026-11-22T07:00:00Z")
                        .param("roomType", "SUITE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].roomNumber").value(305));

        ArgumentCaptor<OffsetDateTime> from = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> to = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(availabilityService).findAvailableRooms(from.capture(), to.capture(), eq(RoomType.SUITE));
        assertTrue(from.getValue().isEqual(OffsetDateTime.parse("2026-11-20T09:00:00Z")));
        assertTrue(to.getValue().isEqual(OffsetDateTime.parse("2026-11-22T07:00:00Z")));
    }

    @Test
    @DisplayName("53. DELETE /api/rooms/{id} returns 204 and deletes the room")
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/rooms/3"))
                .andExpect(status().isNoContent());

        verify(roomService).delete(3L);
    }
}
