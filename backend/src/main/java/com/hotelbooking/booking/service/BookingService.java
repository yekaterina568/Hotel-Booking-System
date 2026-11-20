package com.hotelbooking.booking.service;

import com.hotelbooking.booking.db.BookingEntity;
import com.hotelbooking.booking.db.BookingRepository;
import com.hotelbooking.booking.model.BookingDto;
import com.hotelbooking.booking.model.BookingState;
import com.hotelbooking.room.model.CleaningState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class BookingService {

    /** States in which a booking occupies its room. */
    public static final Set<BookingState> ACTIVE_STATES = Set.of(BookingState.CONFIRMED, BookingState.CHECKEDIN);

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

    @Transactional
    @CacheEvict(value = "bookings", allEntries = true)
    public BookingDto create(BookingEntity booking) {
        if (booking.getGuest() == null || booking.getGuest().getGuestId() == null
                || booking.getRoom() == null || booking.getRoom().getRoomId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите гостя и номер");
        }
        OffsetDateTime arrival = booking.getExpectedArrival();
        if (arrival == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите дату заезда");
        }
        if (booking.getExpectedDeparture() == null) {
            booking.setExpectedDeparture(arrival.plusDays(1));
        }
        OffsetDateTime departure = booking.getExpectedDeparture();
        if (!departure.isAfter(arrival)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Дата выезда должна быть позже даты заезда");
        }

        List<BookingEntity> overlapping = bookingRepository.findOverlapping(
                booking.getRoom().getRoomId(), ACTIVE_STATES, arrival, departure);
        if (!overlapping.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Номер уже занят на эти даты (бронь #" + overlapping.get(0).getBookingId() + ")");
        }

        booking.setState(BookingState.CONFIRMED);
        booking.setCreatedAt(OffsetDateTime.now());
        booking.setCheckedOutAt(null);
        BookingDto created = BookingDto.fromEntity(bookingRepository.save(booking));
        log.info("Created booking id={}", created.bookingId());
        return created;
    }

    @CacheEvict(value = "bookings", allEntries = true)
    public BookingDto checkIn(Long id) {
        BookingEntity booking = findEntityById(id);
        if (booking.getState() != BookingState.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Заселить можно только подтверждённую бронь");
        }
        booking.setState(BookingState.CHECKEDIN);
        BookingDto checkedIn = BookingDto.fromEntity(bookingRepository.save(booking));
        log.info("Checked in booking id={}", id);
        return checkedIn;
    }

    /**
     * Check-out: the guest leaves, the booking becomes CHECKEDOUT, the room is marked DIRTY
     * for housekeeping and all keycards of the booking stop working.
     */
    @Transactional
    @CacheEvict(value = {"bookings", "rooms", "keycards"}, allEntries = true)
    public BookingDto checkOut(Long id) {
        BookingEntity booking = findEntityById(id);
        if (booking.getState() != BookingState.CHECKEDIN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Выселить можно только заселённого гостя");
        }
        booking.setState(BookingState.CHECKEDOUT);
        booking.setCheckedOutAt(OffsetDateTime.now());
        if (booking.getRoom() != null) {
            booking.getRoom().setCleaningState(CleaningState.DIRTY);
        }
        bookingRepository.deactivateKeycards(id);
        BookingDto checkedOut = BookingDto.fromEntity(bookingRepository.save(booking));
        log.info("Checked out booking id={}", id);
        return checkedOut;
    }

    @CacheEvict(value = "bookings", allEntries = true)
    public BookingDto cancel(Long id) {
        BookingEntity booking = findEntityById(id);
        if (booking.getState() != BookingState.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Отменить можно только подтверждённую бронь");
        }
        booking.setState(BookingState.CANCELLED);
        BookingDto cancelled = BookingDto.fromEntity(bookingRepository.save(booking));
        log.info("Cancelled booking id={}", id);
        return cancelled;
    }
}
