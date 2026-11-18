package com.hotelbooking.hotel.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HotelRequest(
        @NotBlank(message = "Hotel name is required.")
        @Size(max = 120, message = "Hotel name must be 120 characters or fewer.")
        String name,

        @NotBlank(message = "City is required.")
        @Size(max = 100, message = "City must be 100 characters or fewer.")
        String city,

        @Size(max = 200, message = "Address must be 200 characters or fewer.")
        String address,

        @Size(max = 2000, message = "Description must be 2,000 characters or fewer.")
        String description
) {
}
