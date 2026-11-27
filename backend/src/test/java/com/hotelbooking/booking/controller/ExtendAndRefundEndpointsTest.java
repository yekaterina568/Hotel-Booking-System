package com.hotelbooking.booking.controller;

import com.hotelbooking.booking.model.BookingDto;
import com.hotelbooking.booking.model.BookingState;
import com.hotelbooking.booking.service.BookingService;
import com.hotelbooking.config.DatabaseConflictErrors;
import com.hotelbooking.refund.controller.BookingRefundController;
import com.hotelbooking.refund.model.RefundDto;
import com.hotelbooking.refund.model.RefundQuote;
import com.hotelbooking.refund.model.RefundState;
import com.hotelbooking.refund.service.RefundPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** REST layer of the new endpoints: extension, refund by the rules and the 409 for a database double booking. */
class ExtendAndRefundEndpointsTest {

    private static final OffsetDateTime ARRIVAL = OffsetDateTime.parse("2027-01-20T14:00:00+05:00");

    private BookingService bookingService;
    private RefundPolicyService refundPolicyService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        bookingService = mock(BookingService.class);
        refundPolicyService = mock(RefundPolicyService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new BookingController(bookingService), new BookingRefundController(refundPolicyService))
                .setControllerAdvice(new DatabaseConflictErrors())
                .build();
    }

    @Test
    @DisplayName("91. POST /api/bookings/{id}/extend passes the new departure and returns the new total")
    void extend_returnsUpdatedBooking() throws Exception {
        OffsetDateTime newDeparture = ARRIVAL.plusDays(5);
        when(bookingService.extend(eq(5L), any(OffsetDateTime.class))).thenReturn(new BookingDto(
                5L, 1L, 10L, ARRIVAL, newDeparture, 5L, new BigDecimal("250.00"), BookingState.CHECKEDIN, null, null));

        mockMvc.perform(post("/api/bookings/5/extend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newDeparture\": \"2027-01-25T14:00:00+05:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nights").value(5))
                .andExpect(jsonPath("$.totalPrice").value(250.00));

        verify(bookingService).extend(eq(5L), argThat(d -> d.isEqual(newDeparture)));
    }

    @Test
    @DisplayName("92. GET /api/bookings/{id}/refund/quote returns percent and amount")
    void quote_returnsJson() throws Exception {
        when(refundPolicyService.quote(5L)).thenReturn(new RefundQuote(5L, BookingState.CANCELLED,
                new BigDecimal("150.00"), ARRIVAL.minusDays(4), 4, 50, new BigDecimal("75.00"), true, "50%"));

        mockMvc.perform(get("/api/bookings/5/refund/quote"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundPercent").value(50))
                .andExpect(jsonPath("$.refundAmount").value(75.00))
                .andExpect(jsonPath("$.eligible").value(true));
    }

    @Test
    @DisplayName("93. POST /api/bookings/{id}/refund without a body returns 201")
    void refund_withoutBody_returns201() throws Exception {
        when(refundPolicyService.requestRefund(eq(5L), isNull())).thenReturn(
                new RefundDto(1L, 5L, new BigDecimal("75.00"), "50%", ARRIVAL.minusDays(4), RefundState.COMPLETED));

        mockMvc.perform(post("/api/bookings/5/refund"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(75.00))
                .andExpect(jsonPath("$.state").value("COMPLETED"));
    }

    @Test
    @DisplayName("94. When the database constraint catches a double booking, the API answers 409, not 500")
    void databaseDoubleBooking_returns409() throws Exception {
        when(bookingService.create(any())).thenThrow(new DataIntegrityViolationException("could not execute statement",
                new SQLException("ERROR: conflicting key value violates exclusion constraint \"excl_bookings_room_no_overlap\"")));

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guest\": {\"guestId\": 1}, \"room\": {\"roomId\": 10},"
                                + " \"expectedArrival\": \"2027-01-20T14:00:00+05:00\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }
}
