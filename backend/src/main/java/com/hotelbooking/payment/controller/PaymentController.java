package com.hotelbooking.payment.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hotelbooking.payment.model.BookingBalanceDto;
import com.hotelbooking.payment.model.PaymentDto;
import com.hotelbooking.payment.model.PaymentOutcome;
import com.hotelbooking.payment.model.PaymentReceiptDto;
import com.hotelbooking.payment.model.PaymentRequest;
import com.hotelbooking.payment.service.PaymentService;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public List<PaymentDto> getAll() {
        return paymentService.findAll();
    }

    @GetMapping("/{id}")
    public PaymentDto getById(@PathVariable Long id) {
        return paymentService.findById(id);
    }

    @GetMapping("/{id}/receipt")
    public PaymentReceiptDto receipt(@PathVariable Long id) {
        return paymentService.receipt(id);
    }

    @GetMapping("/booking/{bookingId}")
    public List<PaymentDto> getByBooking(@PathVariable Long bookingId) {
        return paymentService.findByBooking(bookingId);
    }

    @GetMapping("/booking/{bookingId}/balance")
    public BookingBalanceDto balance(@PathVariable Long bookingId) {
        return paymentService.balance(bookingId);
    }

    /** 201 — платёж создан, 200 — это повтор запроса с тем же Idempotency-Key (вернули прежний платёж). */
    @PostMapping
    public ResponseEntity<PaymentDto> create(@RequestBody PaymentRequest request,
                                             @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        PaymentOutcome outcome = paymentService.create(request, idempotencyKey);
        return ResponseEntity.status(outcome.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(outcome.payment());
    }
}
