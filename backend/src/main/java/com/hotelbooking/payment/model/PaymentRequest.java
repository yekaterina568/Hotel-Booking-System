package com.hotelbooking.payment.model;

import java.math.BigDecimal;

/**
 * Тело POST /api/payments.
 * amount — необязательно: если не указан, оплачивается весь остаток по брони.
 * method — CASH | CARD | TRANSFER.
 * idempotencyKey — необязательно; можно передать и заголовком Idempotency-Key.
 */
public record PaymentRequest(Long bookingId, BigDecimal amount, String method, String idempotencyKey) {
}
