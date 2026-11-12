package com.hotelbooking.payment.db;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {

    java.util.Optional<PaymentEntity> findByIdempotencyKey(String key);
    java.util.Optional<PaymentEntity> findByBookingBookingIdAndState(Long id, String state);

}
