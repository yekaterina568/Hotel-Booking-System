package com.hotelbooking.booking.archive.controller;
import static com.hotelbooking.api.ApiDtos.*;
import com.hotelbooking.booking.archive.service.BookingArchiveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/archive") @RequiredArgsConstructor
public class BookingArchiveController {
    private final BookingArchiveService service;

    @GetMapping public List<ArchiveResponse> getAll() { return service.findAll(); }
    @GetMapping("/{id}") public ArchiveResponse getById(@PathVariable Long id) { return service.findById(id); }
    @PostMapping("/bookings/{id}") public ArchiveResponse archive(@PathVariable Long id) { return service.archive(id); }

}
