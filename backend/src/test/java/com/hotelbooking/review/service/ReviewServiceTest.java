package com.hotelbooking.review.service;

import com.hotelbooking.hotel.db.HotelEntity;
import com.hotelbooking.hotel.service.HotelService;
import com.hotelbooking.review.db.ReviewEntity;
import com.hotelbooking.review.db.ReviewRepository;
import com.hotelbooking.review.model.ReviewDto;
import com.hotelbooking.review.model.ReviewRequest;
import com.hotelbooking.review.model.ReviewStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Unit tests for hotel reviews and their moderation. Repository and HotelService are mocked. */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private HotelService hotelService;

    private ReviewService reviewService;
    private HotelEntity hotel;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(reviewRepository, hotelService);
        hotel = new HotelEntity();
        hotel.setHotelId(1L);
    }

    private ReviewEntity review(long id, ReviewStatus status) {
        ReviewEntity r = new ReviewEntity();
        r.setReviewId(id);
        r.setHotel(hotel);
        r.setAuthorName("Aruzhan");
        r.setRating((short) 4);
        r.setComment("Nice room");
        r.setStatus(status);
        return r;
    }

    @Test
    @DisplayName("44. A new review is saved with trimmed text and waits for moderation (PENDING)")
    void create_newReviewIsPending() {
        when(hotelService.findHotel(1L)).thenReturn(hotel);
        when(reviewRepository.saveAndFlush(any(ReviewEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewDto result = reviewService.create(
                new ReviewRequest(1L, "  Aruzhan ", new BigDecimal("5"), "  Clean and quiet  "));

        assertEquals(1L, (long) result.hotelId());
        assertEquals("Aruzhan", result.authorName());
        assertEquals((short) 5, result.rating());
        assertEquals("Clean and quiet", result.comment());
        assertEquals(ReviewStatus.PENDING, result.status());
    }

    @Test
    @DisplayName("45. Moderator can publish a review")
    void changeStatus_publishesReview() {
        when(reviewRepository.findById(3L)).thenReturn(Optional.of(review(3L, ReviewStatus.PENDING)));

        ReviewDto result = reviewService.changeStatus(3L, ReviewStatus.PUBLISHED);

        assertEquals(ReviewStatus.PUBLISHED, result.status());
    }

    @Test
    @DisplayName("46. A review for an unknown hotel gives 404 and nothing is saved")
    void create_unknownHotel_returns404() {
        when(hotelService.findHotel(99L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Hotel not found."));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reviewService.create(new ReviewRequest(99L, "Ann", new BigDecimal("3"), "Ok")));

        assertEquals(404, ex.getStatusCode().value());
        verify(reviewRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("47. Deleting an unknown review gives 404 and deletes nothing")
    void delete_unknownReview_returns404() {
        when(reviewRepository.findById(8L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reviewService.delete(8L));

        assertEquals(404, ex.getStatusCode().value());
        verify(reviewRepository, never()).delete(any());
    }
}
