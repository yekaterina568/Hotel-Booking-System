package com.hotelbooking.admin;

import com.hotelbooking.booking.model.BookingState;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class AdminBookingService {
    public record BookingRow(long bookingId, long guestId, long roomId, int roomNumber, long hotelId, String hotel,
                             BookingState state, OffsetDateTime createdAt, OffsetDateTime expectedArrival,
                             OffsetDateTime expectedDeparture, OffsetDateTime checkedOutAt) {}
    public record BookingPage(List<BookingRow> rows, long total, int page, int pageSize) {}
    public record StatusChange(BookingState previousState, BookingState newState, OffsetDateTime changedAt) {}
    public record BookingDetail(BookingRow booking, OffsetDateTime archivedAt, List<StatusChange> history) {}

    private final AdminBookingRepository repository;

    AdminBookingService(AdminBookingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public BookingPage search(Long hotelId, BookingState state, String search, int page) {
        if (page < 1 || (hotelId != null && hotelId < 1) || search.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid hotel, search term and page.");
        }
        return repository.search(hotelId, state, search.strip(), page, 25);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public BookingDetail detail(long id) {
        BookingDetail details = repository.detail(id);
        if (details == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found.");
        return details;
    }
}
