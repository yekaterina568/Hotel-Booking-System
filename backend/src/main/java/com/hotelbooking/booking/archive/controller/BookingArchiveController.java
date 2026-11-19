package com.hotelbooking.booking.archive.controller;

import com.hotelbooking.booking.archive.db.BookingArchiveEntity;
import com.hotelbooking.booking.archive.model.BookingArchiveDto;
import com.hotelbooking.booking.archive.service.BookingArchiveService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/archive")
public class BookingArchiveController {

    private final BookingArchiveService archiveService;

    public BookingArchiveController(BookingArchiveService archiveService) {
        this.archiveService = archiveService;
    }

    @GetMapping
    public List<BookingArchiveDto> getAll() {
        return archiveService.findAll();
    }

    @GetMapping("/{id}")
    public BookingArchiveDto getById(@PathVariable Long id) {
        return archiveService.findById(id);
    }

    @PostMapping("/bookings/{bookingId}")
    public BookingArchiveDto archive(@PathVariable Long bookingId) {
        return archiveService.archive(bookingId);
    }
}