package com.hotelbooking.payment.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hotelbooking.payment.db.PaymentEntity;
import com.hotelbooking.payment.dto.PaymentRequest;
import com.hotelbooking.payment.service.PaymentService;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public List<PaymentEntity> getAll() {
        return paymentService.findAll();
    }

    @GetMapping("/{id}")
    public PaymentEntity getById(@PathVariable Long id) {
        return paymentService.findById(id);
    }

    @GetMapping("/booking/{bookingId}")
    public List<PaymentEntity> getByBooking(@PathVariable Long bookingId) {
        return paymentService.findByBooking(bookingId);
    }

    @GetMapping("/booking/{bookingId}/total")
    public Map<String, BigDecimal> totalForBooking(@PathVariable Long bookingId) {
        return Map.of("totalPaid", paymentService.totalPaid(bookingId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentEntity create(@RequestBody PaymentRequest request) {
        return paymentService.create(request);
    }
}
