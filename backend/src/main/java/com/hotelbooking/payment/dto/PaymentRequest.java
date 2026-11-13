package com.hotelbooking.payment.dto;

import java.math.BigDecimal;

/** Тело запроса POST /api/payments. method: CASH | CARD | TRANSFER. */
public record PaymentRequest(Long bookingId, BigDecimal amount, String method) {
}
