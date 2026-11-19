package com.hotelbooking.admin;

import com.hotelbooking.booking.archive.model.BookingArchiveDto;
import com.hotelbooking.booking.archive.service.BookingArchiveService;
import com.hotelbooking.booking.model.BookingDto;
import com.hotelbooking.booking.service.BookingService;
import com.hotelbooking.guest.model.GuestDto;
import com.hotelbooking.guest.service.GuestService;
import com.hotelbooking.keycard.model.KeycardDto;
import com.hotelbooking.keycard.service.KeycardService;
import com.hotelbooking.refund.model.RefundDto;
import com.hotelbooking.refund.service.RefundService;
import com.hotelbooking.room.model.RoomDto;
import com.hotelbooking.room.service.RoomService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminRecordsController {
    private final RoomService rooms;
    private final GuestService guests;
    private final BookingService bookings;
    private final BookingArchiveService archive;
    private final RefundService refunds;
    private final KeycardService keycards;

    public AdminRecordsController(RoomService rooms, GuestService guests, BookingService bookings,
                                  BookingArchiveService archive, RefundService refunds, KeycardService keycards) {
        this.rooms = rooms;
        this.guests = guests;
        this.bookings = bookings;
        this.archive = archive;
        this.refunds = refunds;
        this.keycards = keycards;
    }

    @GetMapping("/rooms")
    public List<RoomDto> rooms() {
        return rooms.findAll();
    }

    @GetMapping("/guests")
    public List<GuestDto> guests() {
        return guests.findAll();
    }

    @GetMapping("/bookings")
    public List<BookingDto> bookings() {
        return bookings.findAll();
    }

    @GetMapping("/booking-archive")
    public List<BookingArchiveDto> bookingArchive() {
        return archive.findAll();
    }

    @GetMapping("/refunds")
    public List<RefundDto> refunds() {
        return refunds.findAll();
    }

    @GetMapping("/keycards")
    public List<KeycardDto> keycards() {
        return keycards.findAll();
    }
}
