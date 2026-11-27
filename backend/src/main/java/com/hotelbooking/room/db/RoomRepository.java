package com.hotelbooking.room.db;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface RoomRepository extends JpaRepository<RoomEntity, Long> {
    @Query(value = "SELECT * FROM rooms WHERE room_id = :id FOR UPDATE", nativeQuery = true)
    Optional<RoomEntity> lockById(long id);

    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM bookings WHERE room_id = :id)
                OR EXISTS (SELECT 1 FROM bookings_archive WHERE room_id = :id)
            """, nativeQuery = true)
    boolean hasBookingHistory(long id);

    boolean existsByHotelIdAndRoomNumber(long hotelId, int roomNumber);
}
