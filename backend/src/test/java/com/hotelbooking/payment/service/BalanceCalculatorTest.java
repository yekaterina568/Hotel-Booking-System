package com.hotelbooking.payment.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Чистая арифметика платежей: БД и Spring не нужны. */
class BalanceCalculatorTest {

    private static final OffsetDateTime ARRIVAL = OffsetDateTime.parse("2026-11-20T14:00:00+05:00");

    @Test
    @DisplayName("Ночи считаются по календарным датам")
    void nightsAreCalendarDays() {
        assertEquals(3, BalanceCalculator.nights(ARRIVAL, OffsetDateTime.parse("2026-11-23T12:00:00+05:00")));
    }

    @Test
    @DisplayName("Меньше суток и отсутствие даты выезда — минимум одна ночь")
    void atLeastOneNight() {
        assertEquals(1, BalanceCalculator.nights(ARRIVAL, OffsetDateTime.parse("2026-11-20T18:00:00+05:00")));
        assertEquals(1, BalanceCalculator.nights(ARRIVAL, null));
    }

    @Test
    @DisplayName("Сумма = ночи × цена; для отменённой брони 0")
    void totalDue() {
        assertEquals(new BigDecimal("150.00"), BalanceCalculator.totalDue(new BigDecimal("50"), 3, false));
        assertEquals(new BigDecimal("0.00"), BalanceCalculator.totalDue(new BigDecimal("50"), 3, true));
    }

    @Test
    @DisplayName("Баланс = сумма - (оплачено - возвращено)")
    void balance() {
        assertEquals(new BigDecimal("50.00"),
                BalanceCalculator.balance(new BigDecimal("150.00"), new BigDecimal("100"), BigDecimal.ZERO));
        assertEquals(new BigDecimal("40.00"),
                BalanceCalculator.balance(new BigDecimal("150.00"), new BigDecimal("150"), new BigDecimal("40")));
    }
}
