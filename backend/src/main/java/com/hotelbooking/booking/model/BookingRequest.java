package com.hotelbooking.booking.model;

import java.time.OffsetDateTime;

/**
 * Body of POST /api/bookings.
 * Example: {"guestId": 1, "roomId": 2, "expectedArrival": "2026-11-20T14:00:00+05:00"}
 * State and createdAt are set by the server, so the client can't send them.
 */
public record BookingRequest(Long guestId, Long roomId, OffsetDateTime expectedArrival) {
}
