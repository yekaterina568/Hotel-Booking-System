package com.hotelbooking.booking.model;
import jakarta.persistence.EnumeratedValue;
public enum BookingState {
    CONFIRMED("Confirmed"), CHECKEDIN("CheckedIn"), CANCELLED("Cancelled"), ARCHIVED("Archived");
    @EnumeratedValue private final String databaseValue;
    BookingState(String databaseValue) { this.databaseValue = databaseValue; }
}
