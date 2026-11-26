package com.hotelbooking.admin;

import com.hotelbooking.hotel.db.HotelRepository;
import com.hotelbooking.room.db.RoomEntity;
import com.hotelbooking.room.db.RoomRepository;
import com.hotelbooking.room.model.RoomDto;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminRoomService {
    private final RoomRepository rooms;
    private final HotelRepository hotels;

    AdminRoomService(RoomRepository rooms, HotelRepository hotels) {
        this.rooms = rooms;
        this.hotels = hotels;
    }

    @Transactional
    @CacheEvict(value = "rooms", allEntries = true)
    public RoomDto create(AdminRoomRequest request) {
        return save(new RoomEntity(), request);
    }

    @Transactional
    @CacheEvict(value = {"rooms", "bookings", "bookingArchive"}, allEntries = true)
    public RoomDto update(long roomId, AdminRoomRequest request) {
        RoomEntity room = rooms.lockById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found."));
        if (room.getHotelId() != request.hotelId().longValueExact() && rooms.hasBookingHistory(roomId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A room with booking history must stay at its original hotel.");
        }
        return save(room, request);
    }

    private RoomDto save(RoomEntity room, AdminRoomRequest request) {
        long hotelId = request.hotelId().longValueExact();
        int roomNumber = request.roomNumber().intValueExact();
        if (!hotels.existsById(hotelId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Hotel not found.");
        }
        boolean sameNumber = room.getHotelId() != null && room.getHotelId() == hotelId
                && room.getRoomNumber() == roomNumber;
        if (!sameNumber && rooms.existsByHotelIdAndRoomNumber(hotelId, roomNumber)) {
            throw duplicateRoom();
        }

        room.setHotelId(hotelId);
        room.setRoomNumber(roomNumber);
        room.setRoomType(request.roomType());
        room.setCleaningState(request.cleaningState());
        room.setPricePerNight(request.pricePerNight());
        try {
            return RoomDto.fromEntity(rooms.saveAndFlush(room));
        } catch (DataIntegrityViolationException exception) {
            Throwable cause = exception.getMostSpecificCause();
            if (cause instanceof java.sql.SQLException sql && "23505".equals(sql.getSQLState())) throw duplicateRoom();
            if (cause instanceof java.sql.SQLException sql && "23503".equals(sql.getSQLState())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The selected hotel is no longer available.");
            }
            throw exception;
        }
    }

    private static ResponseStatusException duplicateRoom() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "That hotel already has a room with this number.");
    }
}
