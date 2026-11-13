package com.hotelbooking.booking.service;

import com.hotelbooking.booking.db.BookingEntity;
import com.hotelbooking.booking.db.BookingRepository;
import com.hotelbooking.booking.model.BookingState;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.math.BigInteger;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;

    public BookingService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    public List<BookingEntity> findAll() {
        return bookingRepository.findAll();
    }

    public BookingEntity findById(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found: " + id));
    }

    public BookingEntity create(BookingEntity booking) {
        booking.setState(BookingState.Confirmed);
        booking.setCreatedAt(OffsetDateTime.now());
        return bookingRepository.save(booking);
    }

    public BookingEntity checkIn(Long id) {
        BookingEntity booking = findById(id);
        if (booking.getState() != BookingState.Confirmed) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Заселить можно только подтверждённую бронь");
        }
        booking.setState(BookingState.CheckedIn);
        return bookingRepository.save(booking);
    }

    public BookingEntity cancel(Long id) {
        BookingEntity booking = findById(id);
        if (booking.getState() != BookingState.Confirmed) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Отменить можно только подтверждённую бронь");
        }
        booking.setState(BookingState.Cancelled);
        return bookingRepository.save(booking);
    }
}