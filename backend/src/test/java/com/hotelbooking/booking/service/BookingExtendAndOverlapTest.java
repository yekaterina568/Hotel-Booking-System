package com.hotelbooking.booking.service;

import com.hotelbooking.booking.db.BookingEntity;
import com.hotelbooking.booking.db.BookingRepository;
import com.hotelbooking.booking.model.BookingDto;
import com.hotelbooking.booking.model.BookingState;
import com.hotelbooking.guest.db.GuestEntity;
import com.hotelbooking.room.db.RoomEntity;
import com.hotelbooking.room.model.CleaningState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Double booking protection and extension of a stay. Repositories are mocked. */
@ExtendWith(MockitoExtension.class)
class BookingExtendAndOverlapTest {

    /** Always in the future, so "new departure is in the past" never triggers by accident. */
    private static final OffsetDateTime ARRIVAL = OffsetDateTime.now().plusDays(10).truncatedTo(ChronoUnit.HOURS);
    private static final OffsetDateTime DEPARTURE = ARRIVAL.plusDays(3);

    @Mock
    private BookingRepository bookingRepository;

    private BookingService bookingService;
    private RoomEntity room;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(bookingRepository);
        room = new RoomEntity();
        room.setRoomId(10L);
        room.setCleaningState(CleaningState.CLEANED);
        room.setPricePerNight(new BigDecimal("50.00"));
    }

    private BookingEntity booking(long id, BookingState state, OffsetDateTime arrival, OffsetDateTime departure) {
        GuestEntity guest = new GuestEntity();
        guest.setGuestId(1L);
        BookingEntity b = new BookingEntity();
        b.setBookingId(id);
        b.setGuest(guest);
        b.setRoom(room);
        b.setState(state);
        b.setExpectedArrival(arrival);
        b.setExpectedDeparture(departure);
        return b;
    }

    @Test
    @DisplayName("69. Double booking: the room is locked BEFORE the overlap check, so parallel requests wait for each other")
    void create_locksRoomBeforeOverlapCheck() {
        BookingEntity request = booking(0L, null, ARRIVAL, DEPARTURE);
        request.setBookingId(null);
        when(bookingRepository.lockRoom(10L)).thenReturn(10L);
        when(bookingRepository.findOverlapping(eq(10L), anyCollection(), eq(ARRIVAL), eq(DEPARTURE)))
                .thenReturn(List.of());
        when(bookingRepository.save(any(BookingEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        bookingService.create(request);

        InOrder order = inOrder(bookingRepository);
        order.verify(bookingRepository).lockRoom(10L);
        order.verify(bookingRepository).findOverlapping(eq(10L), anyCollection(), eq(ARRIVAL), eq(DEPARTURE));
        order.verify(bookingRepository).save(request);
    }

    @Test
    @DisplayName("70. Double booking: a second guest can't book the same room for overlapping dates (409)")
    void create_secondGuestSameRoomOverlappingDates_returns409() {
        BookingEntity first = booking(1L, BookingState.CONFIRMED, ARRIVAL, DEPARTURE);
        BookingEntity second = booking(0L, null, ARRIVAL.plusDays(1), DEPARTURE.plusDays(1));
        second.setBookingId(null);
        when(bookingRepository.lockRoom(10L)).thenReturn(10L);
        when(bookingRepository.findOverlapping(eq(10L), anyCollection(), eq(ARRIVAL.plusDays(1)), eq(DEPARTURE.plusDays(1))))
                .thenReturn(List.of(first));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> bookingService.create(second));

        assertEquals(409, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("#1"));
        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("71. Booking a room that doesn't exist gives 404 and nothing is saved")
    void create_unknownRoom_returns404() {
        BookingEntity request = booking(0L, null, ARRIVAL, DEPARTURE);
        request.setBookingId(null);
        when(bookingRepository.lockRoom(10L)).thenReturn(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> bookingService.create(request));

        assertEquals(404, ex.getStatusCode().value());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("72. Extension: departure moves later and the total price grows (3 -> 5 nights, 150 -> 250)")
    void extend_freeNights_updatesDepartureAndTotal() {
        BookingEntity b = booking(5L, BookingState.CHECKEDIN, ARRIVAL, DEPARTURE);
        OffsetDateTime newDeparture = DEPARTURE.plusDays(2);
        when(bookingRepository.lockById(5L)).thenReturn(Optional.of(b));
        when(bookingRepository.lockRoom(10L)).thenReturn(10L);
        when(bookingRepository.findOverlapping(eq(10L), anyCollection(), eq(DEPARTURE), eq(newDeparture)))
                .thenReturn(List.of());
        when(bookingRepository.save(any(BookingEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingDto result = bookingService.extend(5L, newDeparture);

        assertEquals(newDeparture, result.expectedDeparture());
        assertEquals(5L, (long) result.nights());
        assertEquals(new BigDecimal("250.00"), result.totalPrice());
    }

    @Test
    @DisplayName("73. Extension into another guest's booking is refused (409) and says until when it is possible")
    void extend_intoNextBooking_returns409() {
        BookingEntity b = booking(5L, BookingState.CONFIRMED, ARRIVAL, DEPARTURE);
        BookingEntity next = booking(6L, BookingState.CONFIRMED, DEPARTURE.plusDays(1), DEPARTURE.plusDays(4));
        OffsetDateTime newDeparture = DEPARTURE.plusDays(2);
        when(bookingRepository.lockById(5L)).thenReturn(Optional.of(b));
        when(bookingRepository.lockRoom(10L)).thenReturn(10L);
        when(bookingRepository.findOverlapping(eq(10L), anyCollection(), eq(DEPARTURE), eq(newDeparture)))
                .thenReturn(List.of(next));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.extend(5L, newDeparture));

        assertEquals(409, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("#6"));
        assertEquals(DEPARTURE, b.getExpectedDeparture());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("74. A cancelled or checked-out booking can't be extended (409)")
    void extend_cancelledBooking_returns409() {
        when(bookingRepository.lockById(5L))
                .thenReturn(Optional.of(booking(5L, BookingState.CANCELLED, ARRIVAL, DEPARTURE)));
        when(bookingRepository.lockById(7L))
                .thenReturn(Optional.of(booking(7L, BookingState.CHECKEDOUT, ARRIVAL, DEPARTURE)));

        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> bookingService.extend(5L, DEPARTURE.plusDays(1))).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> bookingService.extend(7L, DEPARTURE.plusDays(1))).getStatusCode().value());
        verify(bookingRepository, never()).lockRoom(any());
    }

    @Test
    @DisplayName("75. The new departure must be later than the current one (400)")
    void extend_notLater_returns400() {
        when(bookingRepository.lockById(5L))
                .thenReturn(Optional.of(booking(5L, BookingState.CONFIRMED, ARRIVAL, DEPARTURE)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.extend(5L, DEPARTURE.minusDays(1)));

        assertEquals(400, ex.getStatusCode().value());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("76. A stay can't be extended beyond 30 nights (400)")
    void extend_beyondMaxStay_returns400() {
        when(bookingRepository.lockById(5L))
                .thenReturn(Optional.of(booking(5L, BookingState.CONFIRMED, ARRIVAL, DEPARTURE)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.extend(5L, ARRIVAL.plusDays(BookingService.MAX_STAY_NIGHTS + 1)));

        assertEquals(400, ex.getStatusCode().value());
        verify(bookingRepository, never()).lockRoom(any());
    }

    @Test
    @DisplayName("77. Extension without a new date gives 400 and doesn't touch the database")
    void extend_withoutDate_returns400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.extend(5L, null));

        assertEquals(400, ex.getStatusCode().value());
        verifyNoInteractions(bookingRepository);
    }

    @Test
    @DisplayName("78. Cancelling a booking stores the cancellation time (used for the refund percentage)")
    void cancel_setsCancelledAt() {
        BookingEntity b = booking(4L, BookingState.CONFIRMED, ARRIVAL, DEPARTURE);
        when(bookingRepository.lockById(4L)).thenReturn(Optional.of(b));
        when(bookingRepository.save(any(BookingEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        bookingService.cancel(4L);

        assertEquals(BookingState.CANCELLED, b.getState());
        assertNotNull(b.getCancelledAt());
    }
}
