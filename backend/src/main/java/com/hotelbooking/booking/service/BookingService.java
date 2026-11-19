package com.hotelbooking.booking.service;

import com.hotelbooking.booking.db.BookingEntity;
import com.hotelbooking.booking.db.BookingRepository;
import com.hotelbooking.booking.model.BookingDto;
import com.hotelbooking.booking.model.BookingState;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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

    @Cacheable("bookings")
    public List<BookingDto> findAll() {
        return bookingRepository.findAll().stream()
                .map(BookingDto::fromEntity)
                .toList();
    }

    @Cacheable("bookings")
    public BookingDto findById(Long id) {
        return BookingDto.fromEntity(findEntityById(id));
    }

    private BookingEntity findEntityById(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Booking not found: " + id));
    }

    @CacheEvict(value = "bookings", allEntries = true)
    public BookingDto create(BookingEntity booking) {
        booking.setState(BookingState.CONFIRMED);
        booking.setCreatedAt(OffsetDateTime.now());
        return BookingDto.fromEntity(bookingRepository.save(booking));
    }

    @CacheEvict(value = "bookings", allEntries = true)
    public BookingDto checkIn(Long id) {
        BookingEntity booking = findEntityById(id);
        if (booking.getState() != BookingState.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Заселить можно только подтверждённую бронь");
        }
        booking.setState(BookingState.CHECKEDIN);
        return BookingDto.fromEntity(bookingRepository.save(booking));
    }

    @CacheEvict(value = "bookings", allEntries = true)
    public BookingDto cancel(Long id) {
        BookingEntity booking = findEntityById(id);
        if (booking.getState() != BookingState.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Отменить можно только подтверждённую бронь");
        }
        booking.setState(BookingState.CANCELLED);
        return BookingDto.fromEntity(bookingRepository.save(booking));
    }
}