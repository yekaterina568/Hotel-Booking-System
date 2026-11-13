package com.hotelbooking.refund.controller;

import com.hotelbooking.refund.db.RefundEntity;
import com.hotelbooking.refund.model.RefundDto;
import com.hotelbooking.refund.service.RefundService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/refunds")
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @GetMapping
    public List<RefundDto> getAll() {
        return refundService.findAll();
    }

    @GetMapping("/{id}")
    public RefundDto getById(@PathVariable Long id) {
        return refundService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RefundDto create(@RequestBody RefundEntity refund) {
        return refundService.create(refund);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        refundService.delete(id);
    }
}