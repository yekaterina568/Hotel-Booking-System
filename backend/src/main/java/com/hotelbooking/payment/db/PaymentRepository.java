package com.hotelbooking.payment.db;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {

    List<PaymentEntity> findAllByOrderByPaidAtDescPaymentIdDesc();

    List<PaymentEntity> findByBookingIdOrderByPaidAtDescPaymentIdDesc(Long bookingId);

    Optional<PaymentEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentEntity> findByRefundId(Long refundId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaymentEntity p "
            + "WHERE p.bookingId = :bookingId AND p.status = :status")
    BigDecimal sumByBookingAndStatus(@Param("bookingId") Long bookingId, @Param("status") String status);
}
