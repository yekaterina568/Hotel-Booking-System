package com.hotelbooking.admin;

import com.hotelbooking.room.model.CleaningState;
import com.hotelbooking.room.model.RoomType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record AdminRoomRequest(
        @NotNull(message = "Choose a hotel.")
        @Positive(message = "Choose a valid hotel.")
        @Digits(integer = 19, fraction = 0, message = "Choose a valid hotel.")
        @Max(value = Long.MAX_VALUE, message = "Choose a valid hotel.") BigDecimal hotelId,
        @NotNull(message = "Enter a room number.")
        @Positive(message = "Room number must be positive.")
        @Digits(integer = 10, fraction = 0, message = "Room number must be a whole number.")
        @Max(value = Integer.MAX_VALUE, message = "Room number is too large.") BigDecimal roomNumber,
        @NotNull(message = "Choose a room type.") RoomType roomType,
        @NotNull(message = "Choose a cleaning status.") CleaningState cleaningState,
        @NotNull(message = "Enter a nightly price.")
        @PositiveOrZero(message = "Nightly price cannot be negative.")
        @Digits(integer = 8, fraction = 2, message = "Use up to eight digits and two decimal places for price.")
        BigDecimal pricePerNight
) {}
