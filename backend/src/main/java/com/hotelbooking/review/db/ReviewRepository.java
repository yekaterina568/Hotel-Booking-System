package com.hotelbooking.review.db;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ReviewRepository extends JpaRepository<ReviewEntity, Long> {
    boolean existsByBookingBookingId(Long id);
}
