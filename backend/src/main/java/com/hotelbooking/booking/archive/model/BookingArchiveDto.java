package com.hotelbooking.booking.archive.model;

import com.hotelbooking.booking.archive.db.BookingArchiveEntity;
import com.hotelbooking.booking.model.BookingState;

import java.time.OffsetDateTime;

public record BookingArchiveDto(
        Long bookingId,
        Long guestId,
        Long roomId,
        OffsetDateTime expectedArrival,
        OffsetDateTime expectedDeparture,
        BookingState state,
        OffsetDateTime createdAt,
        OffsetDateTime checkedOutAt,
        OffsetDateTime archivedAt
) {
    public static BookingArchiveDto fromEntity(BookingArchiveEntity booking) {
        return new BookingArchiveDto(
                booking.getBookingId(),
                booking.getGuest() == null ? null : booking.getGuest().getGuestId(),
                booking.getRoom() == null ? null : booking.getRoom().getRoomId(),
                booking.getExpectedArrival(),
                booking.getExpectedDeparture(),
                booking.getState(),
                booking.getCreatedAt(),
                booking.getCheckedOutAt(),
                booking.getArchivedAt()
        );
    }
}
