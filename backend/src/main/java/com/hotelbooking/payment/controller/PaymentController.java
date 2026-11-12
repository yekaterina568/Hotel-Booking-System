package com.hotelbooking.payment.controller;
import static com.hotelbooking.api.ApiDtos.*;
import com.hotelbooking.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/payments") @RequiredArgsConstructor
public class PaymentController {
    private final PaymentService service;

    @GetMapping public List<PaymentResponse> getAll() { return service.findAll(); }
    @GetMapping("/{id}") public PaymentResponse getById(@PathVariable Long id) { return service.findById(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public PaymentResponse create(@Valid @RequestBody PaymentRequest request) { return service.create(request); }

}
