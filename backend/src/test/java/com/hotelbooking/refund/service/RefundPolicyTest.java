package com.hotelbooking.refund.service;

import com.hotelbooking.booking.db.BookingEntity;
import com.hotelbooking.booking.model.BookingState;
import com.hotelbooking.refund.model.RefundQuote;
import com.hotelbooking.room.db.RoomEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** Cancellation rules: pure calculation, no mocks and no database. */
class RefundPolicyTest {

    private static final OffsetDateTime ARRIVAL = OffsetDateTime.parse("2027-01-20T14:00:00+05:00");

    private BookingEntity cancelledBooking(OffsetDateTime cancelledAt) {
        RoomEntity room = new RoomEntity();
        room.setRoomId(10L);
        room.setPricePerNight(new BigDecimal("50.00"));
        BookingEntity b = new BookingEntity();
        b.setBookingId(5L);
        b.setRoom(room);
        b.setExpectedArrival(ARRIVAL);
        b.setExpectedDeparture(ARRIVAL.plusDays(3));
        b.setState(BookingState.CANCELLED);
        b.setCancelledAt(cancelledAt);
        return b;
    }

    @Test
    @DisplayName("79. Refund %: 7+ days before arrival = 100, 3-6 days = 50, less than 3 days or after = 0")
    void percent_followsTheRules() {
        assertEquals(100, RefundPolicy.percent(ARRIVAL.minusDays(30), ARRIVAL));
        assertEquals(100, RefundPolicy.percent(ARRIVAL.minusDays(7), ARRIVAL));
        assertEquals(50, RefundPolicy.percent(ARRIVAL.minusDays(7).plusMinutes(1), ARRIVAL));
        assertEquals(50, RefundPolicy.percent(ARRIVAL.minusDays(3), ARRIVAL));
        assertEquals(0, RefundPolicy.percent(ARRIVAL.minusDays(3).plusMinutes(1), ARRIVAL));
        assertEquals(0, RefundPolicy.percent(ARRIVAL.plusHours(2), ARRIVAL));
    }

    @Test
    @DisplayName("80. Cancelled 10 days before: full refund of 3 nights x 50 = 150.00")
    void quote_earlyCancellation_fullRefund() {
        RefundQuote quote = RefundPolicy.quote(cancelledBooking(ARRIVAL.minusDays(10)), ARRIVAL.minusDays(1));

        assertTrue(quote.eligible());
        assertEquals(new BigDecimal("150.00"), quote.bookingTotal());
        assertEquals(100, quote.refundPercent());
        assertEquals(new BigDecimal("150.00"), quote.refundAmount());
        assertEquals(10, quote.daysBeforeArrival());
    }

    @Test
    @DisplayName("81. Cancelled 4 days before: half refund = 75.00")
    void quote_lateCancellation_halfRefund() {
        RefundQuote quote = RefundPolicy.quote(cancelledBooking(ARRIVAL.minusDays(4)), ARRIVAL);

        assertTrue(quote.eligible());
        assertEquals(50, quote.refundPercent());
        assertEquals(new BigDecimal("75.00"), quote.refundAmount());
    }

    @Test
    @DisplayName("82. Cancelled 1 day before: no refund, not eligible")
    void quote_lastMinuteCancellation_noRefund() {
        RefundQuote quote = RefundPolicy.quote(cancelledBooking(ARRIVAL.minusDays(1)), ARRIVAL);

        assertFalse(quote.eligible());
        assertEquals(0, quote.refundPercent());
        assertEquals(0, BigDecimal.ZERO.compareTo(quote.refundAmount()));
    }

    @Test
    @DisplayName("83. A booking that isn't cancelled is not eligible, but the quote shows what cancelling now would give")
    void quote_confirmedBooking_notEligibleButShowsAmount() {
        BookingEntity b = cancelledBooking(null);
        b.setState(BookingState.CONFIRMED);

        RefundQuote quote = RefundPolicy.quote(b, ARRIVAL.minusDays(8));

        assertFalse(quote.eligible());
        assertEquals(100, quote.refundPercent());
        assertEquals(new BigDecimal("150.00"), quote.refundAmount());
    }
}
