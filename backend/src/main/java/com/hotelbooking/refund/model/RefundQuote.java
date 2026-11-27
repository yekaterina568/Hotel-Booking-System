package com.hotelbooking.refund.model;

import com.hotelbooking.booking.model.BookingState;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * How much money the guest gets back for a booking, by the cancellation rules.
 *
 * @param bookingTotal      nights x price per night
 * @param cancelledAt       when the booking was cancelled (for a booking that is not cancelled yet: now,
 *                          so the quote shows what the guest would get by cancelling now)
 * @param daysBeforeArrival whole days between cancellation and arrival (negative after arrival)
 * @param refundPercent     100, 50 or 0
 * @param refundAmount      bookingTotal x refundPercent / 100
 * @param eligible          true when a refund can be issued right now
 * @param message           the rule that was applied or the reason why no refund is possible
 */
public record RefundQuote(
        Long bookingId,
        BookingState bookingState,
        BigDecimal bookingTotal,
        OffsetDateTime cancelledAt,
        long daysBeforeArrival,
        int refundPercent,
        BigDecimal refundAmount,
        boolean eligible,
        String message
) {
    public RefundQuote notEligible(String reason) {
        return new RefundQuote(bookingId, bookingState, bookingTotal, cancelledAt, daysBeforeArrival,
                refundPercent, refundAmount, false, reason);
    }
}
