package com.hotelbooking.booking.archive.model;

import com.hotelbooking.booking.archive.db.BookingArchiveEntity;
import com.hotelbooking.booking.model.BookingState;

import java.time.OffsetDateTime;

public record BookingArchiveDto(
        Long bookingId,
        Long guestId,
        Long roomId,
        OffsetDateTime expectedArrival,
        BookingState state,
        OffsetDateTime createdAt,
        OffsetDateTime archivedAt
) {
    public static BookingArchiveDto fromEntity(BookingArchiveEntity booking) {
        return new BookingArchiveDto(
                booking.getBookingId(),
                booking.getGuest() == null ? null : booking.getGuest().getGuestId(),
                booking.getRoom() == null ? null : booking.getRoom().getRoomId(),
                booking.getExpectedArrival(),
                booking.getState(),
                booking.getCreatedAt(),
                booking.getArchivedAt()
        );
    }
}
