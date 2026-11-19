package com.hotelbooking.booking.archive.service;

import com.hotelbooking.booking.archive.db.BookingArchiveEntity;
import com.hotelbooking.booking.archive.db.BookingArchiveRepository;
import com.hotelbooking.booking.archive.model.BookingArchiveDto;
import com.hotelbooking.booking.db.BookingEntity;
import com.hotelbooking.booking.db.BookingRepository;
import com.hotelbooking.booking.model.BookingState;
import com.hotelbooking.guest.db.GuestEntity;
import com.hotelbooking.room.db.RoomEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Unit tests for archiving old bookings. */
@ExtendWith(MockitoExtension.class)
class BookingArchiveServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookingArchiveRepository archiveRepository;

    private BookingArchiveService archiveService;

    @BeforeEach
    void setUp() {
        archiveService = new BookingArchiveService(bookingRepository, archiveRepository);
    }

    private BookingEntity booking(long id, BookingState state) {
        GuestEntity guest = new GuestEntity();
        guest.setGuestId(1L);
        RoomEntity room = new RoomEntity();
        room.setRoomId(10L);

        BookingEntity b = new BookingEntity();
        b.setBookingId(id);
        b.setGuest(guest);
        b.setRoom(room);
        b.setState(state);
        b.setExpectedArrival(OffsetDateTime.parse("2026-11-01T14:00:00+05:00"));
        return b;
    }

    @Test
    @DisplayName("9. Archiving a cancelled booking copies it to the archive and marks it ARCHIVED")
    void archive_cancelledBooking_copiesAndMarksArchived() {
        BookingEntity b = booking(5L, BookingState.CANCELLED);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(b));
        when(archiveRepository.save(any(BookingArchiveEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingArchiveDto result = archiveService.archive(5L);

        assertEquals(5L, (long) result.bookingId());
        assertEquals(1L, (long) result.guestId());
        assertEquals(10L, (long) result.roomId());
        assertEquals(BookingState.ARCHIVED, result.state());
        assertNotNull(result.archivedAt());
        assertEquals(BookingState.ARCHIVED, b.getState());
        verify(bookingRepository).save(b);
    }

    @Test
    @DisplayName("10. An active (CONFIRMED) booking can't be archived (409)")
    void archive_confirmedBooking_returns409() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking(5L, BookingState.CONFIRMED)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> archiveService.archive(5L));

        assertEquals(409, ex.getStatusCode().value());
        verify(archiveRepository, never()).save(any());
    }

    @Test
    @DisplayName("11. A booking can't be archived twice (409)")
    void archive_alreadyArchived_returns409() {
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(booking(5L, BookingState.ARCHIVED)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> archiveService.archive(5L));

        assertEquals(409, ex.getStatusCode().value());
        verify(archiveRepository, never()).save(any());
    }

    @Test
    @DisplayName("12. Archiving an unknown booking gives 404")
    void archive_unknownBooking_returns404() {
        when(bookingRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> archiveService.archive(99L));

        assertEquals(404, ex.getStatusCode().value());
    }
}
