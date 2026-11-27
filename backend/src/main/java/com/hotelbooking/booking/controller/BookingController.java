package com.hotelbooking.booking.controller;

import com.hotelbooking.booking.db.BookingEntity;
import com.hotelbooking.booking.model.BookingDto;
import com.hotelbooking.booking.model.ExtendBookingRequest;
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

    @GetMapping
    public List<BookingDto> getAll() {
        return bookingService.findAll();
    }

    @GetMapping("/{id}")
    public BookingDto getById(@PathVariable Long id) {
        return bookingService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingDto create(@RequestBody BookingEntity booking) {
        return bookingService.create(booking);
    }

    @PostMapping("/{id}/check-in")
    public BookingDto checkIn(@PathVariable Long id) {
        return bookingService.checkIn(id);
    }

    @PostMapping("/{id}/check-out")
    public BookingDto checkOut(@PathVariable Long id) {
        return bookingService.checkOut(id);
    }

    @PostMapping("/{id}/cancel")
    public BookingDto cancel(@PathVariable Long id) {
        return bookingService.cancel(id);
    }

    /** Extends the stay: {"newDeparture": "2026-12-05T12:00:00+05:00"}. */
    @PostMapping("/{id}/extend")
    public BookingDto extend(@PathVariable Long id, @RequestBody ExtendBookingRequest request) {
        return bookingService.extend(id, request == null ? null : request.newDeparture());
    }
}