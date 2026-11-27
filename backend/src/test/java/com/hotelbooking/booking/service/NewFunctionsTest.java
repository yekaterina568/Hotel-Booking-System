package com.hotelbooking.booking.service;

import com.hotelbooking.booking.db.BookingEntity;
import com.hotelbooking.booking.db.BookingRepository;
import com.hotelbooking.booking.model.BookingDto;
import com.hotelbooking.booking.model.BookingState;
import com.hotelbooking.room.db.RoomEntity;
import com.hotelbooking.room.db.RoomRepository;
import com.hotelbooking.room.model.CleaningState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** New functions of this sprint: room search by dates and check-in. Repositories are mocked. */
@ExtendWith(MockitoExtension.class)
class NewFunctionsTest {

    @Mock
    private RoomRepository roomRepository;
    @Mock
    private BookingRepository bookingRepository;

    @Test
    @DisplayName("65. Room search without a check-out date gives 400 and doesn't touch the database")
    void findAvailableRooms_withoutDeparture_returns400() {
        AvailabilityService availability = new AvailabilityService(roomRepository, bookingRepository);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> availability.findAvailableRooms(OffsetDateTime.parse("2027-01-10T14:00:00+05:00"), null, null));

        assertEquals(400, ex.getStatusCode().value());
        verifyNoInteractions(roomRepository, bookingRepository);
    }

    @Test
    @DisplayName("66. A confirmed booking in a cleaned room is checked in")
    void checkIn_cleanedRoom_works() {
        RoomEntity room = new RoomEntity();
        room.setRoomId(10L);
        room.setCleaningState(CleaningState.CLEANED);
        BookingEntity booking = new BookingEntity();
        booking.setBookingId(3L);
        booking.setRoom(room);
        booking.setState(BookingState.CONFIRMED);
        when(bookingRepository.findById(3L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(BookingEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingDto result = new BookingService(bookingRepository).checkIn(3L);

        assertEquals(BookingState.CHECKEDIN, result.state());
    }
}
