package com.hotelbooking.review.service;
import com.hotelbooking.api.*;
import static com.hotelbooking.api.ApiDtos.*;
import static com.hotelbooking.api.ApiErrors.*;
import com.hotelbooking.review.db.*;
import com.hotelbooking.booking.service.BookingService;
import com.hotelbooking.booking.archive.db.BookingArchiveRepository;
import com.hotelbooking.booking.model.BookingState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class ReviewService {
    private final ReviewRepository reviews;
    private final BookingService bookings;
    private final BookingArchiveRepository archives;
    public List<ReviewResponse> findAll() { return reviews.findAll().stream().map(Views::review).toList(); }
    public ReviewResponse findById(Long id) { return Views.review(reviews.findById(id).orElseThrow(() -> missing("Review"))); }
    @Transactional public ReviewResponse create(ReviewRequest r) {
        var b=bookings.locked(r.bookingId());
        if(b.getState()!=BookingState.ARCHIVED || b.getCheckedInAt()==null || !archives.findById(b.getBookingId()).map(a -> a.getState()==BookingState.CHECKEDIN).orElse(false)) throw conflict("Review requires a verified completed stay");
        if(reviews.existsByBookingBookingId(r.bookingId())) throw conflict("Booking already has a review");
        var e=new ReviewEntity(); e.setBooking(b); e.setRating(r.rating()); e.setComment(r.comment()); e.setCreatedAt(OffsetDateTime.now()); return Views.review(reviews.saveAndFlush(e));
    }
}
