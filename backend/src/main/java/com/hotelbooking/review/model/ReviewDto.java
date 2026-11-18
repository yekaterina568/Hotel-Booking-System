package com.hotelbooking.review.model;

import com.hotelbooking.review.db.ReviewEntity;

import java.time.OffsetDateTime;

public record ReviewDto(Long reviewId, Long hotelId, String authorName, short rating, String comment,
                        ReviewStatus status, OffsetDateTime createdAt) {
    public static ReviewDto fromEntity(ReviewEntity review) {
        return new ReviewDto(review.getReviewId(), review.getHotel().getHotelId(), review.getAuthorName(),
                review.getRating(), review.getComment(), review.getStatus(), review.getCreatedAt());
    }
}
