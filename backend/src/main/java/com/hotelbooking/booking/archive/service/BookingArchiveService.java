package com.hotelbooking.booking.archive.service;

import com.hotelbooking.booking.archive.db.BookingArchiveEntity;
import com.hotelbooking.booking.archive.db.BookingArchiveRepository;
import com.hotelbooking.booking.db.BookingEntity;
import com.hotelbooking.booking.db.BookingRepository;
import com.hotelbooking.booking.model.BookingState;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigInteger;
import java.util.List;

@Service
public class BookingArchiveService {

    private final BookingRepository bookingRepository;
    private final BookingArchiveRepository archiveRepository;

    public BookingArchiveService(BookingRepository bookingRepository,
                                 BookingArchiveRepository archiveRepository) {
        this.bookingRepository = bookingRepository;
        this.archiveRepository = archiveRepository;
    }

    public List<BookingArchiveEntity> findAll() {
        return archiveRepository.findAll();
    }

    public BookingArchiveEntity findById(BigInteger id) {
        return archiveRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Archive record not found: " + id));
    }

    @Transactional
    public BookingArchiveEntity archive(BigInteger bookingId) {
        BookingEntity booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found: " + bookingId));

        if (booking.getState() == BookingState.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Нельзя архивировать активную бронь");
        }
        if (booking.getState() == BookingState.ARCHIVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Бронь уже в архиве");
        }

        BookingArchiveEntity archived = new BookingArchiveEntity();
        booking.setState(BookingState.ARCHIVED);
        bookingRepository.save(booking);
        return archiveRepository.save(archived);
    }
}