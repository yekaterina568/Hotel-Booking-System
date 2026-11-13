package com.hotelbooking.booking.db;

import com.hotelbooking.booking.model.BookingState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingRepository extends JpaRepository<BookingEntity, Long> {

    List<BookingEntity> findByState(BookingState state);

    List<BookingEntity> findByGuest_GuestId(Long guestId);

    List<BookingEntity> findByGuest_GuestIdAndState(Long guestId, BookingState state);

    List<BookingEntity> findByRoom_RoomId(Long roomId);
}
