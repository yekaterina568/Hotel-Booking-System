package com.hotelbooking.refund.service;

import com.hotelbooking.booking.db.BookingEntity;
import com.hotelbooking.booking.db.BookingRepository;
import com.hotelbooking.booking.model.BookingState;
import com.hotelbooking.refund.db.RefundEntity;
import com.hotelbooking.refund.db.RefundRepository;
import com.hotelbooking.refund.model.RefundDto;
import com.hotelbooking.refund.model.RefundQuote;
import com.hotelbooking.refund.model.RefundState;
import com.hotelbooking.room.db.RoomEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Refunds by the cancellation rules. Repositories are mocked, the clock is fixed. */
@ExtendWith(MockitoExtension.class)
class RefundPolicyServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2027-01-01T12:00:00+05:00");
    private static final OffsetDateTime ARRIVAL = NOW.plusDays(20);

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private RefundRepository refundRepository;

    private RefundPolicyService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(), ZoneOffset.ofHours(5));
        service = new RefundPolicyService(bookingRepository, refundRepository, clock);
    }

    private BookingEntity booking(BookingState state, OffsetDateTime cancelledAt) {
        RoomEntity room = new RoomEntity();
        room.setRoomId(10L);
        room.setPricePerNight(new BigDecimal("100.00"));
        BookingEntity b = new BookingEntity();
        b.setBookingId(5L);
        b.setRoom(room);
        b.setExpectedArrival(ARRIVAL);
        b.setExpectedDeparture(ARRIVAL.plusDays(2));
        b.setState(state);
        b.setCancelledAt(cancelledAt);
        return b;
    }

    private void bookingIs(BookingEntity b) {
        when(bookingRepository.lockBooking(5L)).thenReturn(5L);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(b));
    }

    @Test
    @DisplayName("84. Cancelled 20 days before arrival: refund of the full 200.00 is saved as COMPLETED")
    void requestRefund_earlyCancellation_savesFullRefund() {
        bookingIs(booking(BookingState.CANCELLED, NOW));
        when(refundRepository.existsByBooking_BookingIdAndStateIn(eq(5L), anyCollection())).thenReturn(false);
        when(refundRepository.save(any(RefundEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RefundDto result = service.requestRefund(5L, "Flight cancelled");

        assertEquals(new BigDecimal("200.00"), result.amount());
        assertEquals(RefundState.COMPLETED, result.state());
        assertEquals(5L, (long) result.bookingId());
        assertNotNull(result.processedAt());
        assertTrue(result.refundReason().contains("100%"));
    }

    @Test
    @DisplayName("85. A booking that isn't cancelled can't be refunded (409), nothing is saved")
    void requestRefund_confirmedBooking_returns409() {
        bookingIs(booking(BookingState.CONFIRMED, null));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.requestRefund(5L, null));

        assertEquals(409, ex.getStatusCode().value());
        verify(refundRepository, never()).save(any());
    }

    @Test
    @DisplayName("86. A second refund for the same booking is refused (409)")
    void requestRefund_alreadyRefunded_returns409() {
        bookingIs(booking(BookingState.CANCELLED, NOW));
        when(refundRepository.existsByBooking_BookingIdAndStateIn(eq(5L), anyCollection())).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.requestRefund(5L, null));

        assertEquals(409, ex.getStatusCode().value());
        verify(refundRepository, never()).save(any());
    }

    @Test
    @DisplayName("87. Cancelled 1 day before arrival: no refund by the rules (409)")
    void requestRefund_lastMinuteCancellation_returns409() {
        bookingIs(booking(BookingState.CANCELLED, ARRIVAL.minusDays(1)));
        when(refundRepository.existsByBooking_BookingIdAndStateIn(eq(5L), anyCollection())).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.requestRefund(5L, null));

        assertEquals(409, ex.getStatusCode().value());
        verify(refundRepository, never()).save(any());
    }

    @Test
    @DisplayName("88. Manual refund bigger than the rules allow is refused (400)")
    void validateManualRefund_tooMuch_returns400() {
        bookingIs(booking(BookingState.CANCELLED, ARRIVAL.minusDays(5)));   // 50% of 200.00 = 100.00
        when(refundRepository.existsByBooking_BookingIdAndStateIn(eq(5L), anyCollection())).thenReturn(false);
        BookingEntity ref = new BookingEntity();
        ref.setBookingId(5L);
        RefundEntity refund = new RefundEntity();
        refund.setBooking(ref);
        refund.setAmount(new BigDecimal("150.00"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.validateManualRefund(refund));

        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("100.00"));
    }

    @Test
    @DisplayName("89. Quote for an unknown booking gives 404")
    void quote_unknownBooking_returns404() {
        when(bookingRepository.findById(404L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.quote(404L));

        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("90. Quote of an already refunded booking says it isn't eligible any more")
    void quote_alreadyRefunded_notEligible() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking(BookingState.CANCELLED, NOW)));
        when(refundRepository.existsByBooking_BookingIdAndStateIn(eq(5L), anyCollection())).thenReturn(true);

        RefundQuote quote = service.quote(5L);

        assertFalse(quote.eligible());
        assertEquals(new BigDecimal("200.00"), quote.refundAmount());
    }
}
