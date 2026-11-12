package com.hotelbooking.cost.controller;
import com.hotelbooking.api.ApiDtos.CostResponse;
import com.hotelbooking.cost.service.CostService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
public class CostController {
    private final CostService costs;
    @GetMapping("/api/bookings/{id}/cost") public CostResponse cost(@PathVariable Long id) { return costs.findByBooking(id); }
}
