package com.hotelbooking.admin;

import com.hotelbooking.booking.archive.service.BookingArchiveService;
import com.hotelbooking.booking.model.BookingState;
import com.hotelbooking.booking.service.BookingService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/bookings")
public class AdminBookingController {
    private final AdminBookingService records;
    private final BookingService bookings;
    private final BookingArchiveService archive;

    AdminBookingController(AdminBookingService records, BookingService bookings, BookingArchiveService archive) {
        this.records = records;
        this.bookings = bookings;
        this.archive = archive;
    }

    @GetMapping("/search")
    public AdminBookingService.BookingPage search(@RequestParam(required = false) Long hotelId,
                                                  @RequestParam(required = false) BookingState state,
                                                  @RequestParam(defaultValue = "") String search,
                                                  @RequestParam(defaultValue = "1") int page) {
        return records.search(hotelId, state, search, page);
    }

    @GetMapping("/{id}")
    public AdminBookingService.BookingDetail detail(@PathVariable long id) {
        return records.detail(id);
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable long id) {
        bookings.cancel(id);
    }

    @PostMapping("/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable long id) {
        archive.archive(id);
    }
}
