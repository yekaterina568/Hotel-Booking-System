package com.hotelbooking.room.model;
import jakarta.persistence.EnumeratedValue;
public enum RoomType {
    SINGLE("Single"), DOUBLE("Double"), TWIN("Twin"), TRIPLE("Triple"), FAMILY("Family"), SUITE("Suite"), DELUXE("Deluxe"), PRESIDENTIAL("Presidential");
    @EnumeratedValue private final String databaseValue;
    RoomType(String databaseValue) { this.databaseValue = databaseValue; }
}
