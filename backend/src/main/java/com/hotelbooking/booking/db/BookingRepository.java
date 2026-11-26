package com.hotelbooking.booking.db;

import com.hotelbooking.booking.model.BookingState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<BookingEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BookingEntity b where b.bookingId = :id")
    Optional<BookingEntity> lockById(long id);


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

    /** Turns off every keycard issued for the booking. Returns how many cards were deactivated. */
    @Modifying
    @Query("update KeycardEntity k set k.isActive = false where k.booking.bookingId = :bookingId and k.isActive = true")
    int deactivateKeycards(@Param("bookingId") Long bookingId);
}
