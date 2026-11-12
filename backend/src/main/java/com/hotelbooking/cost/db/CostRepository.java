package com.hotelbooking.cost.db;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CostRepository extends JpaRepository<CostEntity, Long> {
    java.util.Optional<CostEntity> findByBookingBookingId(Long bookingId);
}
