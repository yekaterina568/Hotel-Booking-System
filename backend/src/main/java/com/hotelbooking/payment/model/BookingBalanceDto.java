package com.hotelbooking.payment.model;

import java.math.BigDecimal;

/**
 * Финансовое состояние брони.
 * balance = totalDue - (totalPaid - totalRefunded). Положительный — гость должен, отрицательный — переплата.
 */
public record BookingBalanceDto(
        Long bookingId,
        String bookingState,
        int nights,
        BigDecimal pricePerNight,
        BigDecimal totalDue,
        BigDecimal totalPaid,
        BigDecimal totalRefunded,
        BigDecimal balance
) {
}
