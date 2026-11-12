package com.hotelbooking.cost.service;
import com.hotelbooking.api.*;
import static com.hotelbooking.api.ApiDtos.*;
import static com.hotelbooking.api.ApiErrors.*;
import com.hotelbooking.cost.db.*;
import com.hotelbooking.booking.db.BookingEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class CostService {
    private final CostRepository costs;
    public CostResponse findByBooking(Long id) { return Views.cost(entity(id)); }
    public CostEntity entity(Long id) { return costs.findByBookingBookingId(id).orElseThrow(() -> missing("Booking cost")); }
    @Transactional public void snapshot(BookingEntity booking) {
        int nights=Math.toIntExact(ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate()));
        var cost=new CostEntity(); cost.setBooking(booking); cost.setNights(nights); cost.setNightlyRate(booking.getRoom().getPricePerNight());
        cost.setSubtotal(cost.getNightlyRate().multiply(BigDecimal.valueOf(nights))); cost.setServiceFee(new BigDecimal("1000.00"));
        cost.setTotal(cost.getSubtotal().add(cost.getServiceFee())); cost.setCurrency("KZT"); cost.setEstimatedLegacy(false); costs.save(cost);
    }
}
