package com.hotelbooking.refund.service;
import com.hotelbooking.api.*;
import static com.hotelbooking.api.ApiDtos.*;
import static com.hotelbooking.api.ApiErrors.*;
import com.hotelbooking.refund.db.*;
import com.hotelbooking.refund.model.RefundState;
import com.hotelbooking.payment.db.PaymentRepository;
import com.hotelbooking.booking.service.BookingService;
import com.hotelbooking.booking.model.BookingState;
import com.hotelbooking.booking.archive.db.BookingArchiveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class RefundService {
    private final RefundRepository refunds;
    private final PaymentRepository payments;
    private final BookingService bookings;
    private final BookingArchiveRepository archives;
    public List<RefundResponse> findAll() { return refunds.findAll().stream().map(Views::refund).toList(); }
    public RefundResponse findById(Long id) { return Views.refund(refunds.findById(id).orElseThrow(() -> missing("Refund"))); }
    @Transactional public RefundResponse create(RefundRequest r) {
        var b=bookings.locked(r.bookingId());
        var existing=refunds.findByIdempotencyKey(r.idempotencyKey());
        if(existing.isPresent()) {
            var e=existing.get(); if(!e.getBooking().getBookingId().equals(r.bookingId()) || e.getAmount().compareTo(r.amount())!=0 || !e.getRefundReason().equals(r.refundReason())) throw conflict("Idempotency key was used for a different request");
            return Views.refund(e);
        }
        boolean cancelled=b.getState()==BookingState.CANCELLED || (b.getState()==BookingState.ARCHIVED && archives.findById(b.getBookingId()).map(a -> a.getState()==BookingState.CANCELLED).orElse(false));
        if(!cancelled) throw conflict("Cancel the booking before requesting a refund");
        var payment=payments.findByBookingBookingIdAndState(r.bookingId(),"SUCCEEDED").orElseThrow(() -> conflict("No successful test payment exists for this booking"));
        var used=refunds.findByBookingBookingId(r.bookingId()).stream().filter(e -> e.getState()!=RefundState.FAILED).map(RefundEntity::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(used.add(r.amount()).compareTo(payment.getAmount())>0) throw conflict("Refund exceeds the remaining paid amount");
        var e=new RefundEntity(); e.setBooking(b); e.setPayment(payment); e.setAmount(r.amount()); e.setRefundReason(r.refundReason());
        e.setIdempotencyKey(r.idempotencyKey()); e.setProcessedAt(OffsetDateTime.now()); e.setState(RefundState.COMPLETED);
        return Views.refund(refunds.saveAndFlush(e));
    }
    public void delete(Long id) { if(!refunds.existsById(id)) throw missing("Refund"); throw conflict("Financial records cannot be deleted"); }
}
