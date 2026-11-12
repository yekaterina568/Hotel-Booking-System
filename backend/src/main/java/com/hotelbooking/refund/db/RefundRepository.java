package com.hotelbooking.refund.db;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RefundRepository extends JpaRepository<RefundEntity, Long> {

    java.util.Optional<RefundEntity> findByIdempotencyKey(String key);
    java.util.List<RefundEntity> findByBookingBookingId(Long bookingId);

}
