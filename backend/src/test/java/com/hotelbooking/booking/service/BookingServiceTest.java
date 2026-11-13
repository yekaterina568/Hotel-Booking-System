package com.hotelbooking.booking.service;

import com.hotelbooking.booking.db.BookingEntity;
import com.hotelbooking.booking.db.BookingRepository;
import com.hotelbooking.booking.model.BookingDto;
import com.hotelbooking.booking.model.BookingRequest;
import com.hotelbooking.booking.model.BookingState;
import com.hotelbooking.guest.db.GuestEntity;
import com.hotelbooking.guest.db.GuestRepository;
import com.hotelbooking.room.db.RoomEntity;
import com.hotelbooking.room.db.RoomRepository;
import com.hotelbooking.room.model.CleaningState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BookingService. The repositories are mocked, so no database is needed.
 * "Now" is fixed to 13 Nov 2026, 14:00 in Almaty (+05:00).
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final ZoneOffset ALMATY = ZoneOffset.ofHours(5);
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 11, 13, 14, 0, 0, 0, ALMATY);
    private static final OffsetDateTime TOMORROW_2PM = NOW.plusDays(1);

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private GuestRepository guestRepository;
    @Mock
    private RoomRepository roomRepository;

    private BookingService bookingService;
    private GuestEntity guest;
    private RoomEntity room;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(Instant.from(NOW), ALMATY);
        bookingService = new BookingService(bookingRepository, guestRepository, roomRepository, fixedClock);

        guest = new GuestEntity();
        guest.setGuestId(1L);
        guest.setFirstName("Aruzhan");
        guest.setLastName("Tanatar");

        room = new RoomEntity();
        room.setRoomId(10L);
        room.setRoomNumber(101);
        room.setCleaningState(CleaningState.CLEANED);
    }

    private BookingEntity booking(long id, BookingState state, OffsetDateTime arrival) {
        BookingEntity b = new BookingEntity();
        b.setBookingId(id);
        b.setGuest(guest);
        b.setRoom(room);
        b.setState(state);
        b.setExpectedArrival(arrival);
        return b;
    }

    private void guestAndRoomExist() {
        when(guestRepository.findById(1L)).thenReturn(Optional.of(guest));
        when(roomRepository.findById(10L)).thenReturn(Optional.of(room));
    }

    private void saveReturnsArgument() {
        when(bookingRepository.save(any(BookingEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private int statusOf(ResponseStatusException ex) {
        return ex.getStatusCode().value();
    }

    // ---------- create ----------

    @Test
    @DisplayName("1. Valid request creates a CONFIRMED booking linked to the guest and the room")
    void create_validRequest_savesConfirmedBooking() {
        guestAndRoomExist();
        when(bookingRepository.findByRoom_RoomId(10L)).thenReturn(List.of());
        saveReturnsArgument();

        BookingDto result = bookingService.create(new BookingRequest(1L, 10L, TOMORROW_2PM));

        assertEquals(BookingState.CONFIRMED, result.state());
        assertEquals(1L, (long) result.guestId());
        assertEquals(10L, (long) result.roomId());
        assertEquals(TOMORROW_2PM, result.expectedArrival());
        assertEquals(NOW, result.createdAt());

        ArgumentCaptor<BookingEntity> saved = ArgumentCaptor.forClass(BookingEntity.class);
        verify(bookingRepository).save(saved.capture());
        assertSame(guest, saved.getValue().getGuest());
        assertSame(room, saved.getValue().getRoom());
    }

    @Test
    @DisplayName("2. Request without guestId is rejected with 400")
    void create_withoutGuestId_returns400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.create(new BookingRequest(null, 10L, TOMORROW_2PM)));

        assertEquals(400, statusOf(ex));
        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("3. Request without expectedArrival is rejected with 400")
    void create_withoutExpectedArrival_returns400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.create(new BookingRequest(1L, 10L, null)));

        assertEquals(400, statusOf(ex));
        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("4. Arrival in the past is rejected with 400")
    void create_arrivalInThePast_returns400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.create(new BookingRequest(1L, 10L, NOW.minusMinutes(1))));

        assertEquals(400, statusOf(ex));
        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("5. Unknown guest gives 404")
    void create_unknownGuest_returns404() {
        when(guestRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.create(new BookingRequest(99L, 10L, TOMORROW_2PM)));

        assertEquals(404, statusOf(ex));
        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("6. Unknown room gives 404")
    void create_unknownRoom_returns404() {
        when(guestRepository.findById(1L)).thenReturn(Optional.of(guest));
        when(roomRepository.findById(77L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.create(new BookingRequest(1L, 77L, TOMORROW_2PM)));

        assertEquals(404, statusOf(ex));
        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("7. Room with a confirmed booking on the same day can't be booked again (409)")
    void create_roomAlreadyBookedSameDay_returns409() {
        guestAndRoomExist();
        BookingEntity existing = booking(5L, BookingState.CONFIRMED, TOMORROW_2PM.withHour(9));
        when(bookingRepository.findByRoom_RoomId(10L)).thenReturn(List.of(existing));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.create(new BookingRequest(1L, 10L, TOMORROW_2PM)));

        assertEquals(409, statusOf(ex));
        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("8. Same room on a different day is allowed")
    void create_roomBookedOnAnotherDay_isAllowed() {
        guestAndRoomExist();
        BookingEntity otherDay = booking(5L, BookingState.CONFIRMED, TOMORROW_2PM.plusDays(3));
        when(bookingRepository.findByRoom_RoomId(10L)).thenReturn(List.of(otherDay));
        saveReturnsArgument();

        BookingDto result = bookingService.create(new BookingRequest(1L, 10L, TOMORROW_2PM));

        assertEquals(BookingState.CONFIRMED, result.state());
    }

    @Test
    @DisplayName("9. A cancelled booking on the same day does not block the room")
    void create_cancelledBookingSameDay_doesNotBlockRoom() {
        guestAndRoomExist();
        BookingEntity cancelled = booking(5L, BookingState.CANCELLED, TOMORROW_2PM);
        when(bookingRepository.findByRoom_RoomId(10L)).thenReturn(List.of(cancelled));
        saveReturnsArgument();

        BookingDto result = bookingService.create(new BookingRequest(1L, 10L, TOMORROW_2PM));

        assertEquals(BookingState.CONFIRMED, result.state());
        verify(bookingRepository).save(any(BookingEntity.class));
    }

    // ---------- check-in ----------

    @Test
    @DisplayName("10. Confirmed booking with a cleaned room becomes CHECKEDIN")
    void checkIn_confirmedWithCleanedRoom_becomesCheckedIn() {
        BookingEntity b = booking(3L, BookingState.CONFIRMED, NOW);
        when(bookingRepository.findById(3L)).thenReturn(Optional.of(b));
        saveReturnsArgument();

        BookingDto result = bookingService.checkIn(3L);

        assertEquals(BookingState.CHECKEDIN, result.state());
    }

    @Test
    @DisplayName("11. Check-in into a room that is not cleaned gives 409")
    void checkIn_roomNotCleaned_returns409() {
        room.setCleaningState(CleaningState.DIRTY);
        BookingEntity b = booking(3L, BookingState.CONFIRMED, NOW);
        when(bookingRepository.findById(3L)).thenReturn(Optional.of(b));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.checkIn(3L));

        assertEquals(409, statusOf(ex));
        assertEquals(BookingState.CONFIRMED, b.getState());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("12. A cancelled booking can't be checked in (409)")
    void checkIn_cancelledBooking_returns409() {
        BookingEntity b = booking(3L, BookingState.CANCELLED, NOW);
        when(bookingRepository.findById(3L)).thenReturn(Optional.of(b));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.checkIn(3L));

        assertEquals(409, statusOf(ex));
        verify(bookingRepository, never()).save(any());
    }

    // ---------- cancel ----------

    @Test
    @DisplayName("13. Confirmed booking can be cancelled")
    void cancel_confirmedBooking_becomesCancelled() {
        BookingEntity b = booking(4L, BookingState.CONFIRMED, TOMORROW_2PM);
        when(bookingRepository.findById(4L)).thenReturn(Optional.of(b));
        saveReturnsArgument();

        BookingDto result = bookingService.cancel(4L);

        assertEquals(BookingState.CANCELLED, result.state());
    }

    @Test
    @DisplayName("14. A checked-in booking can't be cancelled (409)")
    void cancel_checkedInBooking_returns409() {
        BookingEntity b = booking(4L, BookingState.CHECKEDIN, NOW);
        when(bookingRepository.findById(4L)).thenReturn(Optional.of(b));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.cancel(4L));

        assertEquals(409, statusOf(ex));
        assertEquals(BookingState.CHECKEDIN, b.getState());
        verify(bookingRepository, never()).save(any());
    }

    // ---------- read ----------

    @Test
    @DisplayName("15. Unknown booking id gives 404")
    void findById_unknownId_returns404() {
        when(bookingRepository.findById(404L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookingService.findById(404L));

        assertEquals(404, statusOf(ex));
    }

    @Test
    @DisplayName("16. Filter by state uses the state query and returns DTOs")
    void findAll_withStateFilter_usesStateQuery() {
        when(bookingRepository.findByState(BookingState.CANCELLED))
                .thenReturn(List.of(booking(8L, BookingState.CANCELLED, TOMORROW_2PM)));

        List<BookingDto> result = bookingService.findAll(BookingState.CANCELLED, null);

        assertEquals(1, result.size());
        assertEquals(8L, (long) result.get(0).bookingId());
        assertEquals(BookingState.CANCELLED, result.get(0).state());
        verify(bookingRepository, never()).findAll();
    }
}
