package com.hotelbooking.review.model;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ReviewRequest(
        @NotNull(message = "Hotel is required.")
        @Positive(message = "Select a valid hotel.")
        Long hotelId,

        @NotBlank(message = "Author name is required.")
        @Size(max = 100, message = "Author name must be 100 characters or fewer.")
        String authorName,

        @NotNull(message = "Rating is required.")
        @Min(value = 1, message = "Rating must be between 1 and 5.")
        @Max(value = 5, message = "Rating must be between 1 and 5.")
        @Digits(integer = 1, fraction = 0, message = "Rating must be a whole number from 1 to 5.")
        BigDecimal rating,

        @NotBlank(message = "Review text is required.")
        @Size(max = 2000, message = "Review text must be 2,000 characters or fewer.")
        String comment
) {
    public record StatusUpdate(@NotNull(message = "Review status is required.") ReviewStatus status) {
    }
}
