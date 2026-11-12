package com.hotelbooking.room.model;
import jakarta.persistence.EnumeratedValue;
public enum CleaningState {
    CLEANED("Cleaned"), CLEANING("Cleaning"), DIRTY("Dirty");
    @EnumeratedValue private final String databaseValue;
    CleaningState(String databaseValue) { this.databaseValue = databaseValue; }
}
