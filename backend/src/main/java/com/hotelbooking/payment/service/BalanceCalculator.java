package com.hotelbooking.payment.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/** Чистая арифметика платежей, без Spring и БД. */
public final class BalanceCalculator {

    private BalanceCalculator() {
    }

    /**
     * Число ночей = разница календарных дат заезда и выезда, минимум 1.
     * Совпадает с countNights() на фронтенде. Нет даты выезда — считаем одну ночь.
     */
    public static int nights(OffsetDateTime arrival, OffsetDateTime departure) {
        if (arrival == null || departure == null) {
            return 1;
        }
        long days = ChronoUnit.DAYS.between(arrival.toLocalDate(), departure.toLocalDate());
        return (int) Math.max(days, 1);
    }

    /** Сумма за проживание: ночи × цена за ночь (2 знака). Для отменённой брони проживания нет — 0. */
    public static BigDecimal totalDue(BigDecimal pricePerNight, int nights, boolean cancelled) {
        if (cancelled || pricePerNight == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return pricePerNight.multiply(BigDecimal.valueOf(nights)).setScale(2, RoundingMode.HALF_UP);
    }

    /** Остаток к оплате: due - (paid - refunded). Отрицательное значение = гостю нужно вернуть деньги. */
    public static BigDecimal balance(BigDecimal totalDue, BigDecimal paid, BigDecimal refunded) {
        return totalDue.subtract(paid.subtract(refunded)).setScale(2, RoundingMode.HALF_UP);
    }
}
