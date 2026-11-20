package com.hotelbooking.payment.service;

import com.hotelbooking.booking.db.BookingEntity;
import com.hotelbooking.booking.db.BookingRepository;
import com.hotelbooking.booking.model.BookingState;
import com.hotelbooking.payment.db.PaymentEntity;
import com.hotelbooking.payment.db.PaymentRepository;
import com.hotelbooking.payment.model.PaymentOutcome;
import com.hotelbooking.payment.model.PaymentRequest;
import com.hotelbooking.refund.db.RefundEntity;
import com.hotelbooking.room.db.RoomEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Unit tests for PaymentService. Репозитории замоканы, БД не нужна. */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private BookingRepository bookingRepository;

    private PaymentService paymentService;
    private BookingEntity booking;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, bookingRepository);

        RoomEntity room = new RoomEntity();
        room.setRoomId(10L);
        room.setPricePerNight(new BigDecimal("50.00"));

        // 3 ночи по 50.00 = 150.00
        booking = new BookingEntity();
        booking.setBookingId(1L);
        booking.setRoom(room);
        booking.setState(BookingState.CONFIRMED);
        booking.setExpectedArrival(OffsetDateTime.parse("2026-11-20T14:00:00+05:00"));
        booking.setExpectedDeparture(OffsetDateTime.parse("2026-11-23T12:00:00+05:00"));
    }

    private void givenNothingPaidYet() {
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(paymentRepository.sumByBookingAndStatus(1L, "PAID")).thenReturn(BigDecimal.ZERO);
        when(paymentRepository.sumByBookingAndStatus(1L, "REFUNDED")).thenReturn(BigDecimal.ZERO);
    }

    private PaymentEntity stored(long id, BigDecimal amount, String method, String key) {
        PaymentEntity p = new PaymentEntity();
        p.setPaymentId(id);
        p.setBookingId(1L);
        p.setAmount(amount);
        p.setMethod(method);
        p.setStatus("PAID");
        p.setTransactionRef("PAY-TEST");
        p.setIdempotencyKey(key);
        return p;
    }

    @Test
    @DisplayName("Баланс = ночи × цена - оплаты + возвраты")
    void balanceUsesNightsAndPayments() {
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(paymentRepository.sumByBookingAndStatus(1L, "PAID")).thenReturn(new BigDecimal("100.00"));
        when(paymentRepository.sumByBookingAndStatus(1L, "REFUNDED")).thenReturn(new BigDecimal("20.00"));

        var balance = paymentService.balance(1L);

        assertEquals(3, balance.nights());
        assertEquals(new BigDecimal("150.00"), balance.totalDue());
        assertEquals(new BigDecimal("70.00"), balance.balance());
    }

    @Test
    @DisplayName("Без суммы оплачивается весь остаток")
    void withoutAmountPaysFullBalance() {
        givenNothingPaidYet();
        when(paymentRepository.saveAndFlush(any(PaymentEntity.class))).thenAnswer(i -> i.getArgument(0));

        PaymentOutcome outcome = paymentService.create(new PaymentRequest(1L, null, "card", null), null);

        assertFalse(outcome.replayed());
        assertEquals(new BigDecimal("150.00"), outcome.payment().amount());
        assertEquals("CARD", outcome.payment().method());
        assertEquals("PAID", outcome.payment().status());
        assertTrue(outcome.payment().transactionRef().startsWith("PAY-"));
    }

    @Test
    @DisplayName("Сумма больше остатка отклоняется (409), платёж не сохраняется")
    void overpaymentIsRejected() {
        givenNothingPaidYet();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> paymentService.create(new PaymentRequest(1L, new BigDecimal("200.00"), "CASH", null), null));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Оплата отменённой брони запрещена")
    void cancelledBookingCannotBePaid() {
        booking.setState(BookingState.CANCELLED);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> paymentService.create(new PaymentRequest(1L, new BigDecimal("10.00"), "CASH", null), null));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Неизвестный способ оплаты — 400")
    void unknownMethodIsRejected() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> paymentService.create(new PaymentRequest(1L, new BigDecimal("10.00"), "bitcoin", null), null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    @DisplayName("Повтор с тем же ключом возвращает прежний платёж и не создаёт новый")
    void sameKeyReplaysPreviousPayment() {
        PaymentEntity existing = stored(5L, new BigDecimal("150.00"), "CARD", "key-1");
        when(paymentRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        PaymentOutcome outcome = paymentService.create(new PaymentRequest(1L, null, "CARD", null), "key-1");

        assertTrue(outcome.replayed());
        assertEquals(5L, outcome.payment().paymentId());
        verify(paymentRepository, never()).saveAndFlush(any());
        verifyNoInteractions(bookingRepository);
    }

    @Test
    @DisplayName("Тот же ключ с другой суммой — 409")
    void sameKeyDifferentAmountConflicts() {
        PaymentEntity existing = stored(5L, new BigDecimal("150.00"), "CARD", "key-1");
        when(paymentRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> paymentService.create(new PaymentRequest(1L, new BigDecimal("20.00"), "CARD", null), "key-1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    @DisplayName("Гонка двух запросов с одним ключом: проигравший получает платёж победителя")
    void concurrentDuplicateReturnsWinner() {
        PaymentEntity winner = stored(7L, new BigDecimal("150.00"), "CARD", "key-2");
        when(paymentRepository.findByIdempotencyKey("key-2"))
                .thenReturn(Optional.empty(), Optional.of(winner));
        givenNothingPaidYet();
        when(paymentRepository.saveAndFlush(any(PaymentEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        PaymentOutcome outcome = paymentService.create(new PaymentRequest(1L, null, "CARD", null), "key-2");

        assertTrue(outcome.replayed());
        assertEquals(7L, outcome.payment().paymentId());
    }

    @Test
    @DisplayName("Завершённый возврат попадает в журнал как REFUNDED")
    void recordRefundCreatesRefundedRow() {
        when(paymentRepository.findByRefundId(7L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(i -> i.getArgument(0));

        var dto = paymentService.recordRefund(refund(7L, "40.00"));

        assertEquals("REFUNDED", dto.status());
        assertEquals(7L, dto.refundId());
        assertEquals(new BigDecimal("40.00"), dto.amount());
        assertEquals("RFD-00000007", dto.transactionRef());
        assertNull(dto.method());
    }

    @Test
    @DisplayName("Повторная запись того же возврата ничего не дублирует")
    void recordRefundIsIdempotent() {
        PaymentEntity row = new PaymentEntity();
        row.setPaymentId(9L);
        row.setRefundId(7L);
        row.setBookingId(1L);
        row.setAmount(new BigDecimal("40.00"));
        row.setStatus("REFUNDED");
        when(paymentRepository.findByRefundId(7L)).thenReturn(Optional.of(row));

        var dto = paymentService.recordRefund(refund(7L, "40.00"));

        assertEquals(9L, dto.paymentId());
        verify(paymentRepository, never()).save(any());
    }

    private RefundEntity refund(long id, String amount) {
        RefundEntity r = new RefundEntity();
        r.setRefundId(id);
        r.setBooking(booking);
        r.setAmount(new BigDecimal(amount));
        return r;
    }
}
