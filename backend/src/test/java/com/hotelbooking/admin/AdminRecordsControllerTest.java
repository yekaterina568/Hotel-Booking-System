package com.hotelbooking.admin;

import com.hotelbooking.booking.archive.service.BookingArchiveService;
import com.hotelbooking.booking.model.BookingDto;
import com.hotelbooking.booking.model.BookingState;
import com.hotelbooking.booking.service.BookingService;
import com.hotelbooking.guest.model.GuestDto;
import com.hotelbooking.guest.service.GuestService;
import com.hotelbooking.keycard.model.KeycardDto;
import com.hotelbooking.keycard.service.KeycardService;
import com.hotelbooking.refund.model.RefundDto;
import com.hotelbooking.refund.model.RefundState;
import com.hotelbooking.refund.service.RefundService;
import com.hotelbooking.room.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin panel: read-only operational tables (/api/admin/...). Services are mocked. */
class AdminRecordsControllerTest {

    private GuestService guests;
    private BookingService bookings;
    private BookingArchiveService archive;
    private RefundService refunds;
    private KeycardService keycards;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        guests = mock(GuestService.class);
        bookings = mock(BookingService.class);
        archive = mock(BookingArchiveService.class);
        refunds = mock(RefundService.class);
        keycards = mock(KeycardService.class);
        AdminRecordsController controller = new AdminRecordsController(
                mock(RoomService.class), guests, bookings, archive, refunds, keycards);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("61. Admin: the bookings table shows every booking with its state")
    void bookings_returnsAllBookings() throws Exception {
        when(bookings.findAll()).thenReturn(List.of(
                new BookingDto(1L, 1L, 10L, null, null, 2L, null, BookingState.CHECKEDIN, null, null)));

        mockMvc.perform(get("/api/admin/bookings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bookingId").value(1))
                .andExpect(jsonPath("$[0].state").value("CHECKEDIN"));
    }

    @Test
    @DisplayName("62. Admin: the keycards table shows which cards are active")
    void keycards_returnsCards() throws Exception {
        when(keycards.findAll()).thenReturn(List.of(new KeycardDto(3L, 1L, "RFID-0003", null, false)));

        mockMvc.perform(get("/api/admin/keycards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cardUid").value("RFID-0003"))
                .andExpect(jsonPath("$[0].isActive").value(false));
    }

    @Test
    @DisplayName("63. Admin: the guests table lists guests")
    void guests_returnsGuests() throws Exception {
        when(guests.findAll()).thenReturn(List.of(
                new GuestDto(1L, "N1234567", "Aruzhan", "Tanatar", "+77001234567", null)));

        mockMvc.perform(get("/api/admin/guests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lastName").value("Tanatar"));
    }

}
