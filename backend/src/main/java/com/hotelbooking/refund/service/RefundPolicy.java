package com.hotelbooking.refund.service;

import com.hotelbooking.booking.db.BookingEntity;
import com.hotelbooking.booking.model.BookingDto;
import com.hotelbooking.booking.model.BookingState;
import com.hotelbooking.refund.model.RefundQuote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Cancellation rules. Pure calculation without a database, so it is easy to test.
 * <ul>
 *     <li>cancelled 7 or more days before arrival: 100% of the booking price</li>
 *     <li>cancelled 3 to 6 days before arrival: 50%</li>
 *     <li>cancelled less than 3 days before arrival or after it: no refund</li>
 * </ul>
 * Only a cancelled booking gets money back.
 */
public final class RefundPolicy {

    public static final int FULL_REFUND_DAYS = 7;
    public static final int HALF_REFUND_DAYS = 3;

    private RefundPolicy() {
    }

    /** Refund percentage for a cancellation at {@code cancelledAt} of a stay that starts at {@code arrival}. */
    public static int percent(OffsetDateTime cancelledAt, OffsetDateTime arrival) {
        if (cancelledAt == null || arrival == null) {
            return 0;
        }
        long hoursBefore = Duration.between(cancelledAt, arrival).toHours();
        if (hoursBefore >= FULL_REFUND_DAYS * 24L) {
            return 100;
        }
        if (hoursBefore >= HALF_REFUND_DAYS * 24L) {
            return 50;
        }
        return 0;
    }

    /** Price of the whole stay: nights x price per night, or null when the room price is unknown. */
    public static BigDecimal bookingTotal(BookingEntity booking) {
        Long nights = BookingDto.countNights(booking.getExpectedArrival(), booking.getExpectedDeparture());
        BigDecimal price = booking.getRoom() == null ? null : booking.getRoom().getPricePerNight();
        if (nights == null || price == null) {
            return null;
        }
        return price.multiply(BigDecimal.valueOf(nights)).setScale(2, RoundingMode.HALF_UP);
    }

    /** Builds the quote for a booking; {@code now} is used when the booking hasn't been cancelled yet. */
    public static RefundQuote quote(BookingEntity booking, OffsetDateTime now) {
        BookingState state = booking.getState();
        OffsetDateTime cancelledAt = booking.getCancelledAt() != null ? booking.getCancelledAt() : now;
        OffsetDateTime arrival = booking.getExpectedArrival();
        long daysBefore = arrival == null ? 0 : Duration.between(cancelledAt, arrival).toDays();
        int percent = percent(cancelledAt, arrival);
        BigDecimal total = bookingTotal(booking);
        BigDecimal amount = total == null ? BigDecimal.ZERO.setScale(2)
                : total.multiply(BigDecimal.valueOf(percent)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        String rule = switch (percent) {
            case 100 -> "Отмена за " + FULL_REFUND_DAYS + "+ дней до заезда: возврат 100%";
            case 50 -> "Отмена за " + HALF_REFUND_DAYS + "–" + (FULL_REFUND_DAYS - 1) + " дней до заезда: возврат 50%";
            default -> "Отмена менее чем за " + HALF_REFUND_DAYS + " дня до заезда или после него: возврата нет";
        };

        RefundQuote quote = new RefundQuote(booking.getBookingId(), state, total, cancelledAt, daysBefore,
                percent, amount, true, rule);
        if (total == null) {
            return quote.notEligible("Не удалось посчитать стоимость брони: у номера нет цены");
        }
        if (state != BookingState.CANCELLED) {
            return quote.notEligible("Возврат возможен только по отменённой брони. " + rule
                    + (state == BookingState.CONFIRMED ? " (если отменить сейчас)" : ""));
        }
        if (percent == 0) {
            return quote.notEligible(rule);
        }
        return quote;
    }
}
