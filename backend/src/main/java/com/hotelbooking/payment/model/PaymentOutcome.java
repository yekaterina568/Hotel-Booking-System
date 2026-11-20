package com.hotelbooking.payment.model;

/** Результат приёма оплаты: replayed = true, если это повтор запроса с тем же ключом идемпотентности. */
public record PaymentOutcome(PaymentDto payment, boolean replayed) {
}
