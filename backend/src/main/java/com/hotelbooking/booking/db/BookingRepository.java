package com.hotelbooking.booking.db;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BookingRepository extends JpaRepository<BookingEntity, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select b from BookingEntity b where b.bookingId = :id")
    java.util.Optional<BookingEntity> lockById(Long id);
    @org.springframework.data.jpa.repository.Query(value = """
        SELECT count(*) FROM bookings b JOIN rooms r ON r.room_id = b.room_id
        JOIN hotels h ON h.hotel_id = r.hotel_id
        WHERE b.room_id = :roomId AND b.state IN ('Confirmed','CheckedIn')
          AND coalesce(b.check_in_date, (b.expected_arrival AT TIME ZONE h.timezone)::date) < :departure
          AND coalesce(b.check_out_date, (b.expected_arrival AT TIME ZONE h.timezone)::date + 1) > :arrival
        """, nativeQuery = true)
    long countConflicts(Long roomId, java.time.LocalDate arrival, java.time.LocalDate departure);
    java.util.List<BookingEntity> findByStateOrderByExpectedArrivalAscBookingIdAsc(com.hotelbooking.booking.model.BookingState state);

}
