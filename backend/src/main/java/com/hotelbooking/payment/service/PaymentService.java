package com.hotelbooking.payment.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hotelbooking.booking.db.BookingEntity;
import com.hotelbooking.booking.db.BookingRepository;
import com.hotelbooking.booking.model.BookingState;
import com.hotelbooking.guest.db.GuestEntity;
import com.hotelbooking.payment.db.PaymentEntity;
import com.hotelbooking.payment.db.PaymentRepository;
import com.hotelbooking.payment.model.BookingBalanceDto;
import com.hotelbooking.payment.model.PaymentDto;
import com.hotelbooking.payment.model.PaymentMethod;
import com.hotelbooking.payment.model.PaymentOutcome;
import com.hotelbooking.payment.model.PaymentReceiptDto;
import com.hotelbooking.payment.model.PaymentRequest;
import com.hotelbooking.payment.model.PaymentStatus;
import com.hotelbooking.refund.db.RefundEntity;
import com.hotelbooking.room.db.RoomEntity;

@Service
public class PaymentService {

    /** Брони, по которым можно принимать оплату (в т.ч. при выезде). */
    private static final Set<BookingState> PAYABLE_STATES =
            Set.of(BookingState.CONFIRMED, BookingState.CHECKEDIN, BookingState.CHECKEDOUT);

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999.99"); // NUMERIC(10,2)
    private static final int MAX_KEY_LENGTH = 64;

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;

    public PaymentService(PaymentRepository paymentRepository, BookingRepository bookingRepository) {
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
    }

    // ---------------------------------------------------------------- чтение

    public List<PaymentDto> findAll() {
        return paymentRepository.findAllByOrderByPaidAtDescPaymentIdDesc().stream()
                .map(PaymentDto::fromEntity).toList();
    }

    public List<PaymentDto> findByBooking(Long bookingId) {
        return paymentRepository.findByBookingIdOrderByPaidAtDescPaymentIdDesc(bookingId).stream()
                .map(PaymentDto::fromEntity).toList();
    }

    public PaymentDto findById(Long id) {
        return PaymentDto.fromEntity(findEntityById(id));
    }

    /** Сколько гость должен по брони: ночи × цена, минус оплаты, плюс возвраты. */
    public BookingBalanceDto balance(Long bookingId) {
        return computeBalance(findBooking(bookingId));
    }

    /** Данные печатной квитанции для платежа или возврата. */
    public PaymentReceiptDto receipt(Long paymentId) {
        PaymentEntity payment = findEntityById(paymentId);
        BookingEntity booking = findBooking(payment.getBookingId());
        BookingBalanceDto balance = computeBalance(booking);

        GuestEntity guest = booking.getGuest();
        String guestName = guest == null ? "" : (nullToEmpty(guest.getFirstName()) + " "
                + nullToEmpty(guest.getLastName())).trim();
        RoomEntity room = booking.getRoom();

        return new PaymentReceiptDto(
                (PaymentStatus.REFUNDED.name().equals(payment.getStatus()) ? "RFD-" : "RCP-")
                        + String.format("%06d", payment.getPaymentId()),
                payment.getPaymentId(),
                payment.getTransactionRef(),
                payment.getStatus(),
                payment.getMethod(),
                payment.getAmount(),
                payment.getPaidAt(),
                booking.getBookingId(),
                guestName,
                room == null ? null : room.getRoomNumber(),
                room == null || room.getRoomType() == null ? null : room.getRoomType().name(),
                booking.getExpectedArrival(),
                booking.getExpectedDeparture(),
                balance.nights(),
                balance.pricePerNight(),
                balance.totalDue(),
                balance.balance());
    }

    // ---------------------------------------------------------------- оплата

    /**
     * Принять оплату. Не помечен @Transactional намеренно: при гонке двух запросов с одним ключом
     * вторая вставка нарушает UNIQUE(idempotency_key); исключение нужно поймать и вернуть
     * уже сохранённый платёж, а внутри общей транзакции Postgres после ошибки её откатил бы.
     */
    public PaymentOutcome create(PaymentRequest request, String headerKey) {
        if (request == null || request.bookingId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите номер брони (bookingId)");
        }
        String key = normalizeKey(headerKey != null && !headerKey.isBlank() ? headerKey : request.idempotencyKey());

        // Повтор запроса с тем же ключом: возвращаем прежний результат, ничего не списывая второй раз
        if (key != null) {
            Optional<PaymentEntity> existing = paymentRepository.findByIdempotencyKey(key);
            if (existing.isPresent()) {
                return replay(existing.get(), request);
            }
        }

        PaymentMethod method = parseMethod(request.method());
        BookingEntity booking = findBooking(request.bookingId());
        if (!PAYABLE_STATES.contains(booking.getState())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Оплата возможна только для подтверждённой, заселённой или выселенной брони (сейчас: "
                            + booking.getState() + ")");
        }

        BigDecimal due = computeBalance(booking).balance();
        BigDecimal amount;
        if (request.amount() == null) {
            if (due.signum() <= 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Бронь уже полностью оплачена");
            }
            amount = due; // «оплатить остаток»
        } else {
            amount = request.amount().setScale(2, RoundingMode.HALF_UP);
            if (amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Недопустимая сумма оплаты");
            }
            if (due.signum() <= 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Бронь уже полностью оплачена");
            }
            if (amount.compareTo(due) > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Сумма " + amount + " превышает остаток к оплате " + due);
            }
        }

        PaymentEntity payment = new PaymentEntity();
        payment.setBookingId(booking.getBookingId());
        payment.setAmount(amount);
        payment.setMethod(method.name());
        payment.setStatus(PaymentStatus.PAID.name());
        payment.setTransactionRef("PAY-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 16).toUpperCase(Locale.ROOT));
        payment.setIdempotencyKey(key);
        payment.setPaidAt(OffsetDateTime.now());

        try {
            return new PaymentOutcome(PaymentDto.fromEntity(paymentRepository.saveAndFlush(payment)), false);
        } catch (DataIntegrityViolationException e) {
            if (key != null) {
                Optional<PaymentEntity> existing = paymentRepository.findByIdempotencyKey(key);
                if (existing.isPresent()) {
                    return replay(existing.get(), request);
                }
            }
            throw e;
        }
    }

    // ---------------------------------------------------------------- возвраты

    /**
     * Записать завершённый возврат в платёжный журнал (status = REFUNDED).
     * Идемпотентно: для одного refund_id строка создаётся один раз. Вызывается из RefundService.
     */
    @Transactional
    public PaymentDto recordRefund(RefundEntity refund) {
        if (refund == null || refund.getRefundId() == null || refund.getBooking() == null
                || refund.getBooking().getBookingId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Возврат должен быть привязан к брони");
        }
        Optional<PaymentEntity> existing = paymentRepository.findByRefundId(refund.getRefundId());
        if (existing.isPresent()) {
            return PaymentDto.fromEntity(existing.get());
        }
        PaymentEntity row = new PaymentEntity();
        row.setBookingId(refund.getBooking().getBookingId());
        row.setRefundId(refund.getRefundId());
        row.setAmount(refund.getAmount());
        row.setMethod(null);
        row.setStatus(PaymentStatus.REFUNDED.name());
        row.setTransactionRef("RFD-" + String.format("%08d", refund.getRefundId()));
        row.setPaidAt(refund.getProcessedAt() != null ? refund.getProcessedAt() : OffsetDateTime.now());
        return PaymentDto.fromEntity(paymentRepository.save(row));
    }

    // ---------------------------------------------------------------- внутреннее

    private PaymentOutcome replay(PaymentEntity existing, PaymentRequest request) {
        boolean sameBooking = existing.getBookingId().equals(request.bookingId());
        boolean sameMethod = request.method() != null
                && request.method().trim().equalsIgnoreCase(existing.getMethod());
        boolean sameAmount = request.amount() == null
                || request.amount().setScale(2, RoundingMode.HALF_UP).compareTo(existing.getAmount()) == 0;
        if (!sameBooking || !sameMethod || !sameAmount) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ключ идемпотентности уже использован для другого платежа");
        }
        return new PaymentOutcome(PaymentDto.fromEntity(existing), true);
    }

    private BookingBalanceDto computeBalance(BookingEntity booking) {
        int nights = BalanceCalculator.nights(booking.getExpectedArrival(), booking.getExpectedDeparture());
        BigDecimal price = booking.getRoom() == null ? null : booking.getRoom().getPricePerNight();
        BigDecimal totalDue = BalanceCalculator.totalDue(price, nights, booking.getState() == BookingState.CANCELLED);
        BigDecimal paid = paymentRepository.sumByBookingAndStatus(booking.getBookingId(), PaymentStatus.PAID.name());
        BigDecimal refunded = paymentRepository.sumByBookingAndStatus(booking.getBookingId(), PaymentStatus.REFUNDED.name());
        return new BookingBalanceDto(
                booking.getBookingId(),
                booking.getState() == null ? null : booking.getState().name(),
                nights,
                price,
                totalDue,
                paid,
                refunded,
                BalanceCalculator.balance(totalDue, paid, refunded));
    }

    private PaymentEntity findEntityById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found: " + id));
    }

    private BookingEntity findBooking(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found: " + id));
    }

    private static String normalizeKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim();
        if (key.length() > MAX_KEY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ключ идемпотентности длиннее " + MAX_KEY_LENGTH + " символов");
        }
        return key;
    }

    private static PaymentMethod parseMethod(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Укажите способ оплаты: CASH, CARD или TRANSFER");
        }
        try {
            return PaymentMethod.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Неизвестный способ оплаты: " + raw + " (доступно: CASH, CARD, TRANSFER)");
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
