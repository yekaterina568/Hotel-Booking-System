package com.hotelbooking.booking.db;

import com.hotelbooking.booking.model.BookingState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface BookingRepository extends JpaRepository<BookingEntity, Long> {

    /** Bookings of one room in the given states whose stay overlaps [from, to). */
    @Query("""
            select b from BookingEntity b
            where b.room.roomId = :roomId
              and b.state in :states
              and b.expectedArrival < :to
              and b.expectedDeparture > :from
            """)
    List<BookingEntity> findOverlapping(@Param("roomId") Long roomId,
                                        @Param("states") Collection<BookingState> states,
                                        @Param("from") OffsetDateTime from,
                                        @Param("to") OffsetDateTime to);

    /** All bookings in the given states whose stay overlaps [from, to). */
    @Query("""
            select b from BookingEntity b
            where b.state in :states
              and b.expectedArrival < :to
              and b.expectedDeparture > :from
            """)
    List<BookingEntity> findActiveInPeriod(@Param("states") Collection<BookingState> states,
                                           @Param("from") OffsetDateTime from,
                                           @Param("to") OffsetDateTime to);

    /**
     * Locks the room row until the end of the current transaction (SELECT ... FOR UPDATE).
     * Two requests that book or extend the same room are executed one after another, so the second one
     * sees the booking made by the first one in its overlap check. Returns null when the room does not exist.
     */
    @Query(value = "SELECT room_id FROM rooms WHERE room_id = :roomId FOR UPDATE", nativeQuery = true)
    Long lockRoom(@Param("roomId") Long roomId);

    /** Locks the booking row until the end of the transaction, so two refund requests can't run at once. */
    @Query(value = "SELECT booking_id FROM bookings WHERE booking_id = :bookingId FOR UPDATE", nativeQuery = true)
    Long lockBooking(@Param("bookingId") Long bookingId);

    /** Turns off every keycard issued for the booking. Returns how many cards were deactivated. */
    @Modifying
    @Query("update KeycardEntity k set k.isActive = false where k.booking.bookingId = :bookingId and k.isActive = true")
    int deactivateKeycards(@Param("bookingId") Long bookingId);
}
