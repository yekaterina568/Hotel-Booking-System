package com.hotelbooking.refund.controller;

import com.hotelbooking.refund.model.RefundDto;
import com.hotelbooking.refund.model.RefundQuote;
import com.hotelbooking.refund.model.RefundRequest;
import com.hotelbooking.refund.service.RefundPolicyService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Refund of a booking by the cancellation rules: the amount is calculated by the server. */
@RestController
@RequestMapping("/api/bookings/{bookingId}/refund")
public class BookingRefundController {

    private final RefundPolicyService refundPolicyService;

    public BookingRefundController(RefundPolicyService refundPolicyService) {
        this.refundPolicyService = refundPolicyService;
    }

    /** How much the guest would get back. Nothing is saved. */
    @GetMapping("/quote")
    public RefundQuote quote(@PathVariable Long bookingId) {
        return refundPolicyService.quote(bookingId);
    }

    /** Issues the refund. Body is optional: {"reason": "Flight cancelled"}. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RefundDto refund(@PathVariable Long bookingId, @RequestBody(required = false) RefundRequest request) {
        return refundPolicyService.requestRefund(bookingId, request == null ? null : request.reason());
    }
}
