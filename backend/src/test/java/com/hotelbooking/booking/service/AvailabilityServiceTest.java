package com.hotelbooking.booking.service;

import com.hotelbooking.booking.db.BookingEntity;
import com.hotelbooking.booking.db.BookingRepository;
import com.hotelbooking.booking.model.BookingState;
import com.hotelbooking.room.db.RoomEntity;
import com.hotelbooking.room.db.RoomRepository;
import com.hotelbooking.room.model.RoomDto;
import com.hotelbooking.room.model.RoomType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit tests for searching free rooms by dates. Repositories are mocked. */
@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

    private static final OffsetDateTime FROM = OffsetDateTime.parse("2026-11-20T14:00:00+05:00");
    private static final OffsetDateTime TO = FROM.plusDays(2);

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private BookingRepository bookingRepository;

    private AvailabilityService service;

    @BeforeEach
    void setUp() {
        service = new AvailabilityService(roomRepository, bookingRepository);
    }

    private RoomEntity room(long id, int number, RoomType type) {
        RoomEntity r = new RoomEntity();
        r.setRoomId(id);
        r.setRoomNumber(number);
        r.setRoomType(type);
        return r;
    }

    @Test
    @DisplayName("31. A room with an active booking on these dates is not offered")
    void busyRoomIsExcluded() {
        RoomEntity busy = room(1L, 101, RoomType.SINGLE);
        RoomEntity free = room(2L, 102, RoomType.SINGLE);
        BookingEntity booking = new BookingEntity();
        booking.setRoom(busy);
        booking.setState(BookingState.CONFIRMED);
        when(bookingRepository.findActiveInPeriod(anyCollection(), eq(FROM), eq(TO))).thenReturn(List.of(booking));
        when(roomRepository.findAll()).thenReturn(List.of(busy, free));

        List<RoomDto> result = service.findAvailableRooms(FROM, TO, null);

        assertEquals(1, result.size());
        assertEquals(102, result.get(0).roomNumber());
    }

    @Test
    @DisplayName("32. Filter by room type and sort by room number")
    void filtersByTypeAndSorts() {
        when(bookingRepository.findActiveInPeriod(anyCollection(), eq(FROM), eq(TO))).thenReturn(List.of());
        when(roomRepository.findAll()).thenReturn(List.of(
                room(3L, 305, RoomType.SUITE), room(1L, 101, RoomType.SINGLE), room(2L, 204, RoomType.SUITE)));

        List<RoomDto> result = service.findAvailableRooms(FROM, TO, RoomType.SUITE);

        assertEquals(List.of(204, 305), result.stream().map(RoomDto::roomNumber).toList());
    }

    @Test
    @DisplayName("33. Departure not after arrival gives 400")
    void wrongDates_return400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.findAvailableRooms(TO, FROM, null));

        assertEquals(400, ex.getStatusCode().value());
        verifyNoInteractions(roomRepository, bookingRepository);
    }
}
