package com.hotelbooking.refund.model;
import jakarta.persistence.EnumeratedValue;
public enum RefundState {
    PENDING("Pending"), COMPLETED("Completed"), FAILED("Failed");
    @EnumeratedValue private final String databaseValue;
    RefundState(String databaseValue) { this.databaseValue = databaseValue; }
}
