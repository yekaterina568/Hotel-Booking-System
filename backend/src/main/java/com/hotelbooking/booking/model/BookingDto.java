package com.hotelbooking.booking.model;

import com.hotelbooking.booking.db.BookingEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

public record BookingDto(
        Long bookingId,
        Long guestId,
        Long roomId,
        OffsetDateTime expectedArrival,
        OffsetDateTime expectedDeparture,
        Long nights,
        BigDecimal totalPrice,
        BookingState state,
        OffsetDateTime createdAt,
        OffsetDateTime checkedOutAt
) {
    public static BookingDto fromEntity(BookingEntity booking) {
        Long nights = countNights(booking.getExpectedArrival(), booking.getExpectedDeparture());
        BigDecimal price = booking.getRoom() == null ? null : booking.getRoom().getPricePerNight();
        BigDecimal total = (price == null || nights == null) ? null : price.multiply(BigDecimal.valueOf(nights));

        return new BookingDto(
                booking.getBookingId(),
                booking.getGuest() == null ? null : booking.getGuest().getGuestId(),
                booking.getRoom() == null ? null : booking.getRoom().getRoomId(),
                booking.getExpectedArrival(),
                booking.getExpectedDeparture(),
                nights,
                total,
                booking.getState(),
                booking.getCreatedAt(),
                booking.getCheckedOutAt()
        );
    }

    /** Number of nights between arrival and departure dates; a stay shorter than a day counts as one night. */
    public static Long countNights(OffsetDateTime arrival, OffsetDateTime departure) {
        if (arrival == null || departure == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(arrival.toLocalDate(), departure.toLocalDate());
        return Math.max(days, 1);
    }
}
