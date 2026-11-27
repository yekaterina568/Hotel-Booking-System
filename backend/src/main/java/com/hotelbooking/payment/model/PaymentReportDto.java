package com.hotelbooking.payment.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Отчёт по платёжному журналу за период [from, to] (даты включительно).
 * netRevenue = totalPaid - totalRefunded.
 */
public record PaymentReportDto(
        LocalDate from,
        LocalDate to,
        int paymentsCount,
        BigDecimal totalPaid,
        int refundsCount,
        BigDecimal totalRefunded,
        BigDecimal netRevenue,
        int bookingsCount,
        List<MethodTotal> byMethod
) {
    /** Итог по одному способу оплаты (только строки PAID). */
    public record MethodTotal(String method, int count, BigDecimal total) {
    }
}
