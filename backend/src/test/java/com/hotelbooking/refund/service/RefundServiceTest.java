package com.hotelbooking.refund.service;

import com.hotelbooking.booking.db.BookingEntity;
import com.hotelbooking.payment.service.PaymentService;
import com.hotelbooking.refund.db.RefundEntity;
import com.hotelbooking.refund.db.RefundRepository;
import com.hotelbooking.refund.model.RefundDto;
import com.hotelbooking.refund.model.RefundState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** Unit tests for refunds. The repository is mocked, so no database is needed. */
@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock
    private RefundRepository refundRepository;
    @Mock
    private PaymentService paymentService;

    private RefundService refundService;

    @BeforeEach
    void setUp() {
        refundService = new RefundService(refundRepository, paymentService);
    }

    private RefundEntity refund(long bookingId, String amount, RefundState state) {
        BookingEntity booking = new BookingEntity();
        booking.setBookingId(bookingId);
        RefundEntity r = new RefundEntity();
        r.setBooking(booking);
        r.setAmount(new BigDecimal(amount));
        r.setRefundReason("Guest cancelled");
        r.setState(state);
        return r;
    }

    @Test
    @DisplayName("37. Creating a refund saves it and the DTO shows booking, amount, reason and state")
    void create_savesRefundAndReturnsDto() {
        RefundEntity r = refund(5L, "15000.00", RefundState.PENDING);
        when(refundRepository.save(any(RefundEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RefundDto result = refundService.create(r);

        assertEquals(5L, (long) result.bookingId());
        assertEquals(new BigDecimal("15000.00"), result.amount());
        assertEquals("Guest cancelled", result.refundReason());
        assertEquals(RefundState.PENDING, result.state());
        verify(refundRepository).save(r);
        verify(paymentService).validateRefund(r);
        verify(paymentService, never()).recordRefund(any());
    }

    @Test
    @DisplayName("40. A COMPLETED refund is written to the payments ledger (REFUNDED row)")
    void create_completedRefund_isRecordedInLedger() {
        RefundEntity r = refund(5L, "40.00", RefundState.COMPLETED);
        when(refundRepository.save(any(RefundEntity.class))).thenAnswer(inv -> {
            RefundEntity e = inv.getArgument(0);
            e.setRefundId(11L);
            return e;
        });

        RefundDto result = refundService.create(r);

        assertEquals(11L, (long) result.refundId());
        verify(paymentService).validateRefund(r);
        verify(paymentService).recordRefund(r);
    }

    @Test
    @DisplayName("41. An invalid refund (e.g. more than was paid) is rejected and nothing is saved")
    void create_invalidRefund_isNotSaved() {
        RefundEntity r = refund(5L, "999.00", RefundState.COMPLETED);
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "too much"))
                .when(paymentService).validateRefund(r);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> refundService.create(r));

        assertEquals(409, ex.getStatusCode().value());
        verify(refundRepository, never()).save(any());
        verify(paymentService, never()).recordRefund(any());
    }

    @Test
    @DisplayName("42. A client-supplied refundId is ignored so an existing refund cannot be overwritten")
    void create_ignoresClientRefundId() {
        RefundEntity r = refund(5L, "10.00", RefundState.PENDING);
        r.setRefundId(1L);
        when(refundRepository.save(any(RefundEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        refundService.create(r);

        assertNull(r.getRefundId());
    }

    @Test
    @DisplayName("38. findAll turns every refund into a DTO")
    void findAll_mapsEntitiesToDtos() {
        when(refundRepository.findAll()).thenReturn(List.of(
                refund(1L, "100.00", RefundState.COMPLETED), refund(2L, "50.00", RefundState.FAILED)));

        List<RefundDto> result = refundService.findAll();

        assertEquals(2, result.size());
        assertEquals(RefundState.COMPLETED, result.get(0).state());
        assertEquals(2L, (long) result.get(1).bookingId());
    }

    @Test
    @DisplayName("39. Deleting an unknown refund gives 404 and deletes nothing")
    void delete_unknownRefund_returns404() {
        when(refundRepository.existsById(9L)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> refundService.delete(9L));

        assertEquals(404, ex.getStatusCode().value());
        verify(refundRepository, never()).deleteById(anyLong());
    }
}
