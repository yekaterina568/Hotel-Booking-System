package com.hotelbooking.payment.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Данные для печатной квитанции (оплата или возврат). */
public record PaymentReceiptDto(
        String receiptNumber,
        Long paymentId,
        String transactionRef,
        String status,
        String method,
        BigDecimal amount,
        OffsetDateTime paidAt,
        Long bookingId,
        String guestName,
        Integer roomNumber,
        String roomType,
        OffsetDateTime arrival,
        OffsetDateTime departure,
        int nights,
        BigDecimal pricePerNight,
        BigDecimal totalDue,
        BigDecimal balance
) {
}
