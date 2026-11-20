package com.hotelbooking.booking.service;

import com.hotelbooking.booking.db.BookingEntity;
import com.hotelbooking.booking.db.BookingRepository;
import com.hotelbooking.room.db.RoomEntity;
import com.hotelbooking.room.db.RoomRepository;
import com.hotelbooking.room.model.RoomDto;
import com.hotelbooking.room.model.RoomType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


@Service
public class AvailabilityService {

    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;

    public AvailabilityService(RoomRepository roomRepository, BookingRepository bookingRepository) {
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
    }

    public List<RoomDto> findAvailableRooms(OffsetDateTime from, OffsetDateTime to, RoomType roomType) {
        if (from == null || to == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите даты заезда и выезда");
        }
        if (!to.isAfter(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Дата выезда должна быть позже даты заезда");
        }

        Set<Long> busyRoomIds = bookingRepository
                .findActiveInPeriod(BookingService.ACTIVE_STATES, from, to).stream()
                .map(BookingEntity::getRoom)
                .filter(Objects::nonNull)
                .map(RoomEntity::getRoomId)
                .collect(Collectors.toSet());

        return roomRepository.findAll().stream()
                .filter(room -> !busyRoomIds.contains(room.getRoomId()))
                .filter(room -> roomType == null || room.getRoomType() == roomType)
                .sorted(Comparator.comparing(RoomEntity::getRoomNumber,
                        Comparator.nullsLast(Comparator.<Integer>naturalOrder())))
                .map(RoomDto::fromEntity)
                .toList();
    }
}
