package com.hotelbooking.review.service;

import com.hotelbooking.hotel.db.HotelEntity;
import com.hotelbooking.hotel.service.HotelService;
import com.hotelbooking.review.db.ReviewEntity;
import com.hotelbooking.review.db.ReviewRepository;
import com.hotelbooking.review.model.ReviewDto;
import com.hotelbooking.review.model.ReviewRequest;
import com.hotelbooking.review.model.ReviewStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ReviewService {
    private final ReviewRepository reviews;
    private final HotelService hotels;

    public ReviewService(ReviewRepository reviews, HotelService hotels) {
        this.reviews = reviews;
        this.hotels = hotels;
    }

    public List<ReviewDto> findAll() {
        return reviews.findAll(Sort.by(Sort.Direction.DESC, "createdAt", "reviewId"))
                .stream().map(ReviewDto::fromEntity).toList();
    }

    @Transactional
    public ReviewDto create(ReviewRequest request) {
        HotelEntity hotel = hotels.findHotel(request.hotelId());
        ReviewEntity review = new ReviewEntity();
        review.setHotel(hotel);
        review.setAuthorName(request.authorName().strip());
        review.setRating(request.rating().shortValueExact());
        review.setComment(request.comment().strip());
        try {
            return ReviewDto.fromEntity(reviews.saveAndFlush(review));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The hotel is no longer available.", exception);
        }
    }

    @Transactional
    public ReviewDto changeStatus(Long id, ReviewStatus status) {
        ReviewEntity review = findReview(id);
        review.setStatus(status);
        return ReviewDto.fromEntity(review);
    }

    @Transactional
    public void delete(Long id) {
        reviews.delete(findReview(id));
    }

    private ReviewEntity findReview(Long id) {
        return reviews.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found."));
    }
}
