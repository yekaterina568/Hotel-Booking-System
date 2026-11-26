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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Unit tests for BookingService. The repository is mocked, so no database is needed. */
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    private BookingService bookingService;
    private static final OffsetDateTime ARRIVAL = OffsetDateTime.parse("2026-11-20T14:00:00+05:00");

    private GuestEntity guest;
    private RoomEntity room;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(bookingRepository);

        guest = new GuestEntity();
        guest.setGuestId(1L);
        room = new RoomEntity();
        room.setRoomId(10L);
        room.setCleaningState(CleaningState.CLEANED);
        room.setPricePerNight(new BigDecimal("50.00"));
    }

    private BookingEntity booking(long id, BookingState state) {
        BookingEntity b = new BookingEntity();
        b.setBookingId(id);
        b.setGuest(guest);
        b.setRoom(room);
        b.setState(state);
        return b;
    }

    private void saveReturnsArgument() {
        when(bookingRepository.save(any(BookingEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("1. A new booking always gets state CONFIRMED and a createdAt time")
    void create_setsConfirmedStateAndCreatedAt() {
        saveReturnsArgument();
        BookingEntity request = booking(1L, BookingState.CANCELLED); // client tries to send another state
        request.setExpectedArrival(ARRIVAL);
        request.setCreatedAt(null);

        BookingDto result = bookingService.create(request);

        assertEquals(BookingState.CONFIRMED, result.state());
        assertNotNull(result.createdAt());
    }

    @Test
    @DisplayName("2. Created booking DTO contains guestId, roomId and arrival time")
    void create_returnsDtoWithIds() {
        saveReturnsArgument();
        OffsetDateTime arrival = OffsetDateTime.parse("2026-11-20T14:00:00+05:00");
        BookingEntity request = booking(1L, null);
        request.setExpectedArrival(arrival);

        BookingDto result = bookingService.create(request);

        assertEquals(1L, (long) result.guestId());
        assertEquals(10L, (long) result.roomId());
        assertEquals(arrival, result.expectedArrival());
        verify(bookingRepository).save(request);
    }

    @Test
    @DisplayName("3. Confirmed booking becomes CHECKEDIN after check-in")
    void checkIn_confirmedBooking_becomesCheckedIn() {
        when(bookingRepository.lockById(3L)).thenReturn(Optional.of(booking(3L, BookingState.CONFIRMED)));
        saveReturnsArgument();

        BookingDto result = bookingService.checkIn(3L);

        assertEquals(BookingState.CHECKEDIN, result.state());
    }

    @Test
    @DisplayName("4. A cancelled booking can't be checked in (409)")
    void checkIn_cancelledBooking_returns409() {
        when(bookingRepository.lockById(3L)).thenReturn(Optional.of(booking(3L, BookingState.CANCELLED)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.checkIn(3L));

        assertEquals(409, ex.getStatusCode().value());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("5. Confirmed booking becomes CANCELLED after cancel")
    void cancel_confirmedBooking_becomesCancelled() {
        when(bookingRepository.lockById(4L)).thenReturn(Optional.of(booking(4L, BookingState.CONFIRMED)));
        saveReturnsArgument();

        BookingDto result = bookingService.cancel(4L);

        assertEquals(BookingState.CANCELLED, result.state());
    }

    @Test
    @DisplayName("6. A checked-in booking can't be cancelled (409)")
    void cancel_checkedInBooking_returns409() {
        BookingEntity b = booking(4L, BookingState.CHECKEDIN);
        when(bookingRepository.lockById(4L)).thenReturn(Optional.of(b));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.cancel(4L));

        assertEquals(409, ex.getStatusCode().value());
        assertEquals(BookingState.CHECKEDIN, b.getState());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("7. Unknown booking id gives 404")
    void findById_unknownId_returns404() {
        when(bookingRepository.findById(404L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.findById(404L));

        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("8. findAll turns every booking into a DTO")
    void findAll_mapsEntitiesToDtos() {
        when(bookingRepository.findAll()).thenReturn(List.of(
                booking(1L, BookingState.CONFIRMED), booking(2L, BookingState.CANCELLED)));

        List<BookingDto> result = bookingService.findAll();

        assertEquals(2, result.size());
        assertEquals(1L, (long) result.get(0).bookingId());
        assertEquals(BookingState.CANCELLED, result.get(1).state());
    }

    // ---------- Reservation: dates and overlapping bookings ----------

    @Test
    @DisplayName("24. Without a departure date the booking is for one night")
    void create_withoutDeparture_defaultsToOneNight() {
        saveReturnsArgument();
        BookingEntity request = booking(1L, null);
        request.setExpectedArrival(ARRIVAL);

        BookingDto result = bookingService.create(request);

        assertEquals(ARRIVAL.plusDays(1), result.expectedDeparture());
        assertEquals(1L, (long) result.nights());
    }

    @Test
    @DisplayName("25. Departure before arrival is rejected (400) and nothing is saved")
    void create_departureBeforeArrival_returns400() {
        BookingEntity request = booking(1L, null);
        request.setExpectedArrival(ARRIVAL);
        request.setExpectedDeparture(ARRIVAL.minusHours(3));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.create(request));

        assertEquals(400, ex.getStatusCode().value());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("26. A room that is already booked for these dates can't be booked again (409)")
    void create_overlappingBooking_returns409() {
        BookingEntity request = booking(2L, null);
        request.setExpectedArrival(ARRIVAL);
        request.setExpectedDeparture(ARRIVAL.plusDays(3));
        when(bookingRepository.findOverlapping(eq(10L), anyCollection(), eq(ARRIVAL), eq(ARRIVAL.plusDays(3))))
                .thenReturn(List.of(booking(1L, BookingState.CONFIRMED)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.create(request));

        assertEquals(409, ex.getStatusCode().value());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("27. Booking without a guest or room is rejected (400)")
    void create_withoutRoom_returns400() {
        BookingEntity request = booking(1L, null);
        request.setRoom(null);
        request.setExpectedArrival(ARRIVAL);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.create(request));

        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("28. Total price = nights x price per night")
    void dto_calculatesNightsAndTotalPrice() {
        BookingEntity b = booking(1L, BookingState.CONFIRMED);
        b.setExpectedArrival(ARRIVAL);
        b.setExpectedDeparture(ARRIVAL.plusDays(3));

        BookingDto dto = BookingDto.fromEntity(b);

        assertEquals(3L, (long) dto.nights());
        assertEquals(new BigDecimal("150.00"), dto.totalPrice());
    }

    // ---------- Check-out ----------

    @Test
    @DisplayName("29. Check-out: booking becomes CHECKEDOUT, room DIRTY, keycards are deactivated")
    void checkOut_checkedInBooking_becomesCheckedOut() {
        when(bookingRepository.lockById(5L)).thenReturn(Optional.of(booking(5L, BookingState.CHECKEDIN)));
        saveReturnsArgument();

        BookingDto result = bookingService.checkOut(5L);

        assertEquals(BookingState.CHECKEDOUT, result.state());
        assertNotNull(result.checkedOutAt());
        assertEquals(CleaningState.DIRTY, room.getCleaningState());
        verify(bookingRepository).deactivateKeycards(5L);
    }

    @Test
    @DisplayName("30. A guest who hasn't checked in can't check out (409)")
    void checkOut_confirmedBooking_returns409() {
        when(bookingRepository.lockById(6L)).thenReturn(Optional.of(booking(6L, BookingState.CONFIRMED)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.checkOut(6L));

        assertEquals(409, ex.getStatusCode().value());
        assertEquals(CleaningState.CLEANED, room.getCleaningState());
        verify(bookingRepository, never()).save(any());
        verify(bookingRepository, never()).deactivateKeycards(any());
    }
}
