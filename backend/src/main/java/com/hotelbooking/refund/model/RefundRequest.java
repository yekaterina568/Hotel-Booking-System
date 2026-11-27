package com.hotelbooking.refund.model;

/** Optional body of POST /api/bookings/{id}/refund. */
public record RefundRequest(String reason) {
}
