package com.hotelbooking.booking.controller;

import com.hotelbooking.booking.model.BookingDto;
import com.hotelbooking.booking.model.BookingRequest;
import com.hotelbooking.booking.model.BookingState;
import com.hotelbooking.booking.service.BookingService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    // GET /api/bookings, /api/bookings?state=CONFIRMED, /api/bookings?guestId=5
    @GetMapping
    public List<BookingDto> getAll(@RequestParam(required = false) BookingState state,
                                   @RequestParam(required = false) Long guestId) {
        return bookingService.findAll(state, guestId);
    }

    @GetMapping("/{id}")
    public BookingDto getById(@PathVariable Long id) {
        return bookingService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingDto create(@RequestBody BookingRequest request) {
        return bookingService.create(request);
    }

    @PostMapping("/{id}/check-in")
    public BookingDto checkIn(@PathVariable Long id) {
        return bookingService.checkIn(id);
    }

    @PostMapping("/{id}/cancel")
    public BookingDto cancel(@PathVariable Long id) {
        return bookingService.cancel(id);
    }
}
