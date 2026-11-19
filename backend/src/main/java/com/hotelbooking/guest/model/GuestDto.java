package com.hotelbooking.guest.model;

import com.hotelbooking.guest.db.GuestEntity;

import java.time.OffsetDateTime;

public record GuestDto(
        Long guestId,
        String passportNum,
        String firstName,
        String lastName,
        String phoneNum,
        OffsetDateTime createdAt
) {
    public static GuestDto fromEntity(GuestEntity guest) {
        return new GuestDto(
                guest.getGuestId(),
                guest.getPassportNum(),
                guest.getFirstName(),
                guest.getLastName(),
                guest.getPhoneNum(),
                guest.getCreatedAt()
        );
    }
}
