package com.hotelbooking.booking.model;

/** Имена констант совпадают с метками enum в PostgreSQL (регистр важен для Hibernate). */
public enum BookingState {
    Confirmed,
    CheckedIn,
    Cancelled,
    Archived
}
