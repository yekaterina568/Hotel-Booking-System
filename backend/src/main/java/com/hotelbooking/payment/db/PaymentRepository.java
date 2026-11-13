package com.hotelbooking.payment.db;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {

    List<PaymentEntity> findAllByOrderByPaidAtDesc();

    List<PaymentEntity> findByBookingIdOrderByPaidAtDesc(Long bookingId);

    // Состояние брони читаем как текст: не зависит от регистра значений enum в БД/Java.
    @Query(value = "SELECT CAST(state AS TEXT) FROM bookings WHERE booking_id = :id", nativeQuery = true)
    Optional<String> findBookingState(@Param("id") Long bookingId);

    @Query(value = "SELECT COALESCE(SUM(amount), 0) FROM payments WHERE booking_id = :id AND status = 'PAID'",
            nativeQuery = true)
    BigDecimal sumPaidByBooking(@Param("id") Long bookingId);
}
