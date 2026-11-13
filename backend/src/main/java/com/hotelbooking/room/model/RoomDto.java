package com.hotelbooking.room.model;

import com.hotelbooking.room.db.RoomEntity;

import java.math.BigDecimal;

public record RoomDto(
        Long roomId,
        Integer roomNumber,
        RoomType roomType,
        CleaningState cleaningState,
        BigDecimal pricePerNight
) {
    public static RoomDto fromEntity(RoomEntity room) {
        return new RoomDto(
                room.getRoomId(),
                room.getRoomNumber(),
                room.getRoomType(),
                room.getCleaningState(),
                room.getPricePerNight()
        );
    }
}
