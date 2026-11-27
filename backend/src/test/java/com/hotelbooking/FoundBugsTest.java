package com.hotelbooking;

import com.hotelbooking.booking.db.BookingEntity;
import com.hotelbooking.booking.db.BookingRepository;
import com.hotelbooking.booking.model.BookingState;
import com.hotelbooking.booking.service.BookingService;
import com.hotelbooking.refund.db.RefundEntity;
import com.hotelbooking.refund.db.RefundRepository;
import com.hotelbooking.refund.service.RefundService;
import com.hotelbooking.room.db.RoomEntity;
import com.hotelbooking.room.model.CleaningState;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;

/**
 * Tests that found bugs in the application code. See docs/week13-test-report.md.
 * They are switched off with @Disabled so the build stays green until the developers fix the bugs.
 * After the fix: delete the @Disabled line and the test must pass.
 */
@ExtendWith(MockitoExtension.class)
class FoundBugsTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private RefundRepository refundRepository;

    @Test
    @Disabled("BUG-1: check-in into a dirty room is allowed. Waiting for a fix in BookingService.checkIn")
    @DisplayName("67. BUG-1: a guest must not be checked into a DIRTY room (expected 409)")
    void checkIn_dirtyRoom_shouldBeRejected() {
        RoomEntity room = new RoomEntity();
        room.setRoomId(10L);
        room.setCleaningState(CleaningState.DIRTY);
        BookingEntity booking = new BookingEntity();
        booking.setBookingId(3L);
        booking.setRoom(room);
        booking.setState(BookingState.CONFIRMED);
        lenient().when(bookingRepository.findById(3L)).thenReturn(Optional.of(booking));
        lenient().when(bookingRepository.save(booking)).thenReturn(booking);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> new BookingService(bookingRepository).checkIn(3L));

        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    @Disabled("BUG-2: a refund with a negative amount is saved. Waiting for a fix in RefundService.create")
    @DisplayName("68. BUG-2: a refund with a negative amount must be rejected (expected 400)")
    void create_negativeRefund_shouldBeRejected() {
        BookingEntity booking = new BookingEntity();
        booking.setBookingId(5L);
        RefundEntity refund = new RefundEntity();
        refund.setBooking(booking);
        refund.setAmount(new BigDecimal("-50.00"));
        lenient().when(refundRepository.save(refund)).thenReturn(refund);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> new RefundService(refundRepository).create(refund));

        assertEquals(400, ex.getStatusCode().value());
    }
}
