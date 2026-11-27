package com.hotelbooking.hotel.controller;

import com.hotelbooking.admin.AdminApiErrors;
import com.hotelbooking.hotel.model.HotelDto;
import com.hotelbooking.hotel.model.HotelRequest;
import com.hotelbooking.hotel.service.HotelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin panel: hotel management API (/api/admin/hotels). The service is mocked. */
class HotelAdminControllerTest {

    private HotelService hotelService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        hotelService = mock(HotelService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new HotelAdminController(hotelService))
                .setControllerAdvice(new AdminApiErrors())
                .build();
    }

    @Test
    @DisplayName("54. Admin: GET /api/admin/hotels returns all hotels")
    void findAll_returnsHotels() throws Exception {
        when(hotelService.findAll()).thenReturn(List.of(
                new HotelDto(1L, "Grand Almaty", "Almaty", null, null, true)));

        mockMvc.perform(get("/api/admin/hotels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Grand Almaty"))
                .andExpect(jsonPath("$[0].defaultHotel").value(true));
    }

    @Test
    @DisplayName("55. Admin: creating a valid hotel returns 201")
    void create_validHotel_returns201() throws Exception {
        when(hotelService.create(any(HotelRequest.class)))
                .thenReturn(new HotelDto(2L, "Sea View", "Aktau", null, null, false));

        mockMvc.perform(post("/api/admin/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Sea View", "city": "Aktau"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hotelId").value(2));
    }

    @Test
    @DisplayName("56. Admin: a hotel without a name is rejected (400) before reaching the service")
    void create_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "   ", "city": "Aktau"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Hotel name is required."));

        verifyNoInteractions(hotelService);
    }

    @Test
    @DisplayName("57. Admin: deleting the default hotel returns 409 with the reason")
    void delete_defaultHotel_returns409() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "The default hotel is used for room bookings."))
                .when(hotelService).delete(1L);

        mockMvc.perform(delete("/api/admin/hotels/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The default hotel is used for room bookings."));
    }

}
