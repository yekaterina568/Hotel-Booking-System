package com.hotelbooking.refund.model;

import com.hotelbooking.refund.db.RefundEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record RefundDto(
        Long refundId,
        Long bookingId,
        BigDecimal amount,
        String refundReason,
        OffsetDateTime processedAt,
        RefundState state
) {
    public static RefundDto fromEntity(RefundEntity refund) {
        return new RefundDto(
                refund.getRefundId(),
                refund.getBooking() == null ? null : refund.getBooking().getBookingId(),
                refund.getAmount(),
                refund.getRefundReason(),
                refund.getProcessedAt(),
                refund.getState()
        );
    }
}
