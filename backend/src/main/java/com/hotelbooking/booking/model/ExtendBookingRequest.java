package com.hotelbooking.booking.model;

import java.time.OffsetDateTime;

/** Body of POST /api/bookings/{id}/extend: the new (later) departure date and time. */
public record ExtendBookingRequest(OffsetDateTime newDeparture) {
}
