package com.hotelbooking.booking.archive.service;

import com.hotelbooking.booking.archive.db.BookingArchiveEntity;
import com.hotelbooking.booking.archive.db.BookingArchiveRepository;
import com.hotelbooking.booking.archive.model.BookingArchiveDto;
import com.hotelbooking.booking.db.BookingEntity;
import com.hotelbooking.booking.db.BookingRepository;
import com.hotelbooking.booking.model.BookingState;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
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

    @Cacheable("bookingArchive")
    public List<BookingArchiveDto> findAll() {
        return archiveRepository.findAll().stream().map(BookingArchiveDto::fromEntity).toList();
    }

    @Cacheable("bookingArchive")
    public BookingArchiveDto findById(Long id) {
        BookingArchiveEntity archived = archiveRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Archive record not found: " + id));
        return BookingArchiveDto.fromEntity(archived);
    }

    @Transactional
    @CacheEvict(value = {"bookingArchive", "bookings"}, allEntries = true)
    public BookingArchiveDto archive(Long bookingId) {
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
        archived.setBookingId(booking.getBookingId());
        archived.setGuest(booking.getGuest());
        archived.setRoom(booking.getRoom());
        archived.setExpectedArrival(booking.getExpectedArrival());
        archived.setState(BookingState.ARCHIVED);
        archived.setCreatedAt(booking.getCreatedAt());
        archived.setArchivedAt(OffsetDateTime.now());
        booking.setState(BookingState.ARCHIVED);
        bookingRepository.save(booking);
        return BookingArchiveDto.fromEntity(archiveRepository.save(archived));
    }
}