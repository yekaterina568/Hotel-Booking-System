package com.hotelbooking.refund.db;

import com.hotelbooking.refund.model.RefundState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;


public interface RefundRepository extends JpaRepository<RefundEntity, Long> {

    /** True when the booking already has a refund in one of the given states. */
    boolean existsByBooking_BookingIdAndStateIn(Long bookingId, Collection<RefundState> states);
}
