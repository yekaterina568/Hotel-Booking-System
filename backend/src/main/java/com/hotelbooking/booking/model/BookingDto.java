package com.hotelbooking.booking.model;

import com.hotelbooking.booking.db.BookingEntity;

import java.time.OffsetDateTime;

public record BookingDto(
        Long bookingId,
        Long guestId,
        Long roomId,
        OffsetDateTime expectedArrival,
        BookingState state,
        OffsetDateTime createdAt
) {
    public static BookingDto fromEntity(BookingEntity booking) {
        return new BookingDto(
                booking.getBookingId(),
                booking.getGuest() == null ? null : booking.getGuest().getGuestId(),
                booking.getRoom() == null ? null : booking.getRoom().getRoomId(),
                booking.getExpectedArrival(),
                booking.getState(),
                booking.getCreatedAt()
        );
    }
}
