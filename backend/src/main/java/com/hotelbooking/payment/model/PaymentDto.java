package com.hotelbooking.payment.model;

import com.hotelbooking.payment.db.PaymentEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentDto(
        Long paymentId,
        Long bookingId,
        Long refundId,
        BigDecimal amount,
        String method,
        String status,
        String transactionRef,
        OffsetDateTime paidAt
) {
    public static PaymentDto fromEntity(PaymentEntity p) {
        return new PaymentDto(p.getPaymentId(), p.getBookingId(), p.getRefundId(), p.getAmount(),
                p.getMethod(), p.getStatus(), p.getTransactionRef(), p.getPaidAt());
    }
}
