package com.hotelbooking.room.dto;

/** Тело запроса PUT /api/rooms/{id}/cleaning-state. cleaningState: Cleaned | Cleaning | Dirty (регистр не важен). */
public record CleaningStateRequest(String cleaningState) {
}
