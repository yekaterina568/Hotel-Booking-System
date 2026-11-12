package com.hotelbooking.booking.controller;
import static com.hotelbooking.api.ApiDtos.*;
import com.hotelbooking.booking.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/bookings") @RequiredArgsConstructor
public class BookingController {
    private final BookingService service;

    @GetMapping public List<BookingResponse> getAll() { return service.findAll(); }
    @GetMapping("/{id}") public BookingResponse getById(@PathVariable Long id) { return service.findById(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public BookingResponse create(@Valid @RequestBody BookingRequest request) { return service.create(request); }

    @PostMapping("/{id}/check-in") public BookingResponse checkIn(@PathVariable Long id) { return service.checkIn(id); }
    @PostMapping("/{id}/cancel") public BookingResponse cancel(@PathVariable Long id) { return service.cancel(id); }
    @GetMapping("/priority-queue") public List<BookingResponse> priorityQueue() { return service.priorityQueue(); }
    @GetMapping("/availability") public AvailabilityResponse availability(@RequestParam Long roomId, @RequestParam java.time.LocalDate checkInDate, @RequestParam java.time.LocalDate checkOutDate) {
        return service.availability(roomId,checkInDate,checkOutDate);
    }

}
