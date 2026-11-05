package com.hotelbooking.booking.controller;

import com.hotelbooking.booking.db.BookingEntity;
import com.hotelbooking.booking.service.BookingService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;
import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping
    public List<BookingEntity> getAll() {
        return bookingService.findAll();
    }

    @GetMapping("/{id}")
    public BookingEntity getById(@PathVariable BigInteger id) {
        return bookingService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingEntity create(@RequestBody BookingEntity booking) {
        return bookingService.create(booking);
    }

    @PostMapping("/{id}/check-in")
    public BookingEntity checkIn(@PathVariable BigInteger id) {
        return bookingService.checkIn(id);
    }

    @PostMapping("/{id}/cancel")
    public BookingEntity cancel(@PathVariable BigInteger id) {
        return bookingService.cancel(id);
    }
}