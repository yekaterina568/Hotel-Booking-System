package com.hotelbooking.payment.service;
import com.hotelbooking.api.*;
import static com.hotelbooking.api.ApiDtos.*;
import static com.hotelbooking.api.ApiErrors.*;
import com.hotelbooking.payment.db.*;
import com.hotelbooking.booking.service.BookingService;
import com.hotelbooking.booking.model.BookingState;
import com.hotelbooking.cost.service.CostService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class PaymentService {
    private final PaymentRepository payments;
    private final BookingService bookings;
    private final CostService costs;
    public List<PaymentResponse> findAll() { return payments.findAll().stream().map(Views::payment).toList(); }
    public PaymentResponse findById(Long id) { return Views.payment(payments.findById(id).orElseThrow(() -> missing("Payment"))); }
    @Transactional public PaymentResponse create(PaymentRequest r) {
        var b=bookings.locked(r.bookingId());
        var existing=payments.findByIdempotencyKey(r.idempotencyKey());
        if(existing.isPresent()) {
            var e=existing.get();
            if(!e.getBooking().getBookingId().equals(r.bookingId()) || e.getState().equals("DECLINED")!=r.simulateDecline()) throw conflict("Idempotency key was used for a different request");
            return Views.payment(e);
        }
        if(b.getState()!=BookingState.CONFIRMED && b.getState()!=BookingState.CHECKEDIN) throw conflict("Payment requires an active booking");
        if(payments.findByBookingBookingIdAndState(r.bookingId(),"SUCCEEDED").isPresent()) throw conflict("Booking has already been paid");
        var cost=costs.entity(r.bookingId()); if(cost.isEstimatedLegacy()) throw conflict("Legacy booking cost requires review before payment");
        var e=new PaymentEntity(); e.setBooking(b); e.setAmount(cost.getTotal()); e.setCurrency(cost.getCurrency());
        e.setState(r.simulateDecline()?"DECLINED":"SUCCEEDED"); e.setIdempotencyKey(r.idempotencyKey());
        e.setProviderReference("TEST-"+UUID.randomUUID().toString().replace("-","")); e.setCreatedAt(OffsetDateTime.now()); return Views.payment(payments.saveAndFlush(e));
    }
}
