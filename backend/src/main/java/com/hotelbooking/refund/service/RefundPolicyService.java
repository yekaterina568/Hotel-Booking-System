package com.hotelbooking.refund.service;

import com.hotelbooking.booking.db.BookingEntity;
import com.hotelbooking.booking.db.BookingRepository;
import com.hotelbooking.booking.model.BookingState;
import com.hotelbooking.refund.db.RefundEntity;
import com.hotelbooking.refund.db.RefundRepository;
import com.hotelbooking.refund.model.RefundDto;
import com.hotelbooking.refund.model.RefundQuote;
import com.hotelbooking.refund.model.RefundState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Set;

/**
 * Refunds by the cancellation rules of {@link RefundPolicy}: the amount is calculated from the booking,
 * only a cancelled booking is refunded, and a booking gets at most one refund.
 */
@Service
@Slf4j
public class RefundPolicyService {

    /** Refunds in these states count as "money already returned or on its way". FAILED may be retried. */
    static final Set<RefundState> ACTIVE_REFUND_STATES = Set.of(RefundState.PENDING, RefundState.COMPLETED);

    private final BookingRepository bookingRepository;
    private final RefundRepository refundRepository;
    private final Clock clock;

    @Autowired
    public RefundPolicyService(BookingRepository bookingRepository, RefundRepository refundRepository) {
        this(bookingRepository, refundRepository, Clock.systemDefaultZone());
    }

    /** For tests: a fixed clock makes "days before arrival" predictable. */
    RefundPolicyService(BookingRepository bookingRepository, RefundRepository refundRepository, Clock clock) {
        this.bookingRepository = bookingRepository;
        this.refundRepository = refundRepository;
        this.clock = clock;
    }

    /** Shows how much would be refunded for the booking, without saving anything. */
    public RefundQuote quote(Long bookingId) {
        BookingEntity booking = findBooking(bookingId);
        RefundQuote quote = RefundPolicy.quote(booking, OffsetDateTime.now(clock));
        if (alreadyRefunded(bookingId)) {
            return quote.notEligible("По этой брони возврат уже оформлен");
        }
        return quote;
    }

    /** Issues the refund calculated by the rules. The guest gets the money back right away (state COMPLETED). */
    @Transactional
    @CacheEvict(value = "refunds", allEntries = true)
    public RefundDto requestRefund(Long bookingId, String reason) {
        lockBookingOrThrow(bookingId);
        BookingEntity booking = findBooking(bookingId);
        RefundQuote quote = RefundPolicy.quote(booking, OffsetDateTime.now(clock));

        if (booking.getState() != BookingState.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Сначала отмените бронь: возврат возможен только по отменённой брони");
        }
        if (alreadyRefunded(bookingId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "По этой брони возврат уже оформлен");
        }
        if (!quote.eligible()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Возврат не положен: " + quote.message());
        }

        RefundEntity refund = new RefundEntity();
        refund.setBooking(booking);
        refund.setAmount(quote.refundAmount());
        refund.setRefundReason(reason == null || reason.isBlank()
                ? quote.message()
                : reason.trim() + " (" + quote.refundPercent() + "% по правилам отмены)");
        refund.setState(RefundState.COMPLETED);
        refund.setProcessedAt(OffsetDateTime.now(clock));
        RefundDto created = RefundDto.fromEntity(refundRepository.save(refund));
        log.info("Refund id={} for booking id={}: {}% = {}", created.refundId(), bookingId,
                quote.refundPercent(), quote.refundAmount());
        return created;
    }

    /**
     * Checks a refund entered by hand (POST /api/refunds): the booking must be cancelled, have no refund yet,
     * and the amount can't be more than the rules allow. Must run inside a transaction (the booking row is locked).
     */
    public void validateManualRefund(RefundEntity refund) {
        Long bookingId = refund.getBooking().getBookingId();
        lockBookingOrThrow(bookingId);
        BookingEntity booking = findBooking(bookingId);
        if (booking.getState() != BookingState.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Возврат возможен только по отменённой брони");
        }
        if (alreadyRefunded(bookingId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "По этой брони возврат уже оформлен");
        }
        RefundQuote quote = RefundPolicy.quote(booking, OffsetDateTime.now(clock));
        BigDecimal max = quote.refundAmount();
        if (refund.getAmount().compareTo(max) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Сумма возврата больше допустимой: максимум " + max + " (" + quote.refundPercent() + "%). "
                            + quote.message());
        }
        refund.setBooking(booking);
    }

    private boolean alreadyRefunded(Long bookingId) {
        return refundRepository.existsByBooking_BookingIdAndStateIn(bookingId, ACTIVE_REFUND_STATES);
    }

    private BookingEntity findBooking(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found: " + bookingId));
    }

    private void lockBookingOrThrow(Long bookingId) {
        if (bookingRepository.lockBooking(bookingId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found: " + bookingId);
        }
    }
}
