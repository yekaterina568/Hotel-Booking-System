package com.hotelbooking.payment.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hotelbooking.payment.db.PaymentEntity;
import com.hotelbooking.payment.db.PaymentRepository;
import com.hotelbooking.payment.dto.PaymentRequest;
import com.hotelbooking.payment.model.PaymentMethod;
import com.hotelbooking.payment.model.PaymentStatus;

@Service
public class PaymentService {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999.99"); // NUMERIC(10,2)

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public List<PaymentEntity> findAll() {
        return paymentRepository.findAllByOrderByPaidAtDesc();
    }

    public PaymentEntity findById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Payment not found: " + id));
    }

    public List<PaymentEntity> findByBooking(Long bookingId) {
        return paymentRepository.findByBookingIdOrderByPaidAtDesc(bookingId);
    }

    public BigDecimal totalPaid(Long bookingId) {
        return paymentRepository.sumPaidByBooking(bookingId);
    }

    /**
     * Принять оплату по брони. Всё в одной транзакции: либо запись в payments
     * сохранена, либо (при любой ошибке) откат.
     */
    @Transactional
    public PaymentEntity create(PaymentRequest request) {
        if (request == null || request.bookingId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите номер брони (bookingId)");
        }
        if (request.amount() == null || request.amount().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Сумма оплаты должна быть больше 0");
        }
        BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Недопустимая сумма оплаты");
        }

        PaymentMethod method = parseMethod(request.method());

        String bookingState = paymentRepository.findBookingState(request.bookingId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found: " + request.bookingId()));
        String state = bookingState.toUpperCase(Locale.ROOT);
        if (!state.equals("CONFIRMED") && !state.equals("CHECKEDIN")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Оплата возможна только для подтверждённой или заселённой брони (сейчас: " + bookingState + ")");
        }

        PaymentEntity payment = new PaymentEntity();
        payment.setBookingId(request.bookingId());
        payment.setAmount(amount);
        payment.setMethod(method.name());
        payment.setStatus(PaymentStatus.PAID.name());
        payment.setTransactionRef("PAY-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 16).toUpperCase(Locale.ROOT));
        payment.setPaidAt(OffsetDateTime.now());
        return paymentRepository.save(payment);
    }

    private PaymentMethod parseMethod(String raw) {
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
}
