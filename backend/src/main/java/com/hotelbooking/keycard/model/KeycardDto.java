package com.hotelbooking.keycard.model;

import com.hotelbooking.keycard.db.KeycardEntity;

import java.time.OffsetDateTime;

public record KeycardDto(
        Long keycardId,
        Long bookingId,
        String cardUid,
        OffsetDateTime issueTimestamp,
        Boolean isActive
) {
    public static KeycardDto fromEntity(KeycardEntity keycard) {
        return new KeycardDto(
                keycard.getKeycardId(),
                keycard.getBooking() == null ? null : keycard.getBooking().getBookingId(),
                keycard.getCardUid(),
                keycard.getIssueTimestamp(),
                keycard.getIsActive()
        );
    }
}
