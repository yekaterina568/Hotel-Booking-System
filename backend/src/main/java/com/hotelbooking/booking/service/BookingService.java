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
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class BookingService {

    /** States in which a booking occupies its room. */
    public static final Set<BookingState> ACTIVE_STATES = Set.of(BookingState.CONFIRMED, BookingState.CHECKEDIN);

    /** A stay (also after an extension) can't be longer than this. */
    public static final long MAX_STAY_NIGHTS = 30;

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

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

        Long nights = BookingDto.countNights(arrival, departure);
        if (nights > MAX_STAY_NIGHTS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Максимальный срок проживания — " + MAX_STAY_NIGHTS + " ночей");
        }

        // Double booking protection: lock the room first, so a parallel request for the same room
        // waits here and then sees this booking in its overlap check.
        lockRoomOrThrow(booking.getRoom().getRoomId());
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
        booking.setCancelledAt(OffsetDateTime.now());
        BookingDto cancelled = BookingDto.fromEntity(bookingRepository.save(booking));
        log.info("Cancelled booking id={}", id);
        return cancelled;
    }

    /**
     * Extends a stay: moves the departure date later. Works for confirmed and checked-in bookings.
     * The extra nights must be free: the room is locked and the period [old departure, new departure)
     * is checked against the other active bookings of the room. The new total price is in the returned DTO.
     */
    @Transactional
    @CacheEvict(value = "bookings", allEntries = true)
    public BookingDto extend(Long id, OffsetDateTime newDeparture) {
        if (newDeparture == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите новую дату выезда");
        }
        BookingEntity booking = findEntityById(id);
        if (!ACTIVE_STATES.contains(booking.getState())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Продлить можно только подтверждённую бронь или бронь заселённого гостя");
        }
        if (booking.getRoom() == null || booking.getRoom().getRoomId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "У брони нет номера");
        }

        OffsetDateTime arrival = booking.getExpectedArrival();
        OffsetDateTime currentDeparture = booking.getExpectedDeparture() != null
                ? booking.getExpectedDeparture()
                : arrival.plusDays(1);
        if (!newDeparture.isAfter(currentDeparture)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Новая дата выезда должна быть позже текущей (" + currentDeparture.format(DATE_TIME) + ")");
        }
        if (!newDeparture.isAfter(OffsetDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Новая дата выезда уже в прошлом");
        }
        if (BookingDto.countNights(arrival, newDeparture) > MAX_STAY_NIGHTS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Максимальный срок проживания — " + MAX_STAY_NIGHTS + " ночей");
        }

        Long roomId = booking.getRoom().getRoomId();
        lockRoomOrThrow(roomId);
        // Our own booking ends at currentDeparture, so with half-open periods it never overlaps the extension.
        BookingEntity firstConflict = bookingRepository
                .findOverlapping(roomId, ACTIVE_STATES, currentDeparture, newDeparture).stream()
                .filter(other -> !id.equals(other.getBookingId()))
                .min(Comparator.comparing(BookingEntity::getExpectedArrival))
                .orElse(null);
        if (firstConflict != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Нельзя продлить: номер занят с " + firstConflict.getExpectedArrival().format(DATE_TIME)
                            + " (бронь #" + firstConflict.getBookingId() + "). Продлить можно максимум до "
                            + firstConflict.getExpectedArrival().format(DATE_TIME));
        }

        Long oldNights = BookingDto.countNights(arrival, currentDeparture);
        booking.setExpectedDeparture(newDeparture);
        BookingDto extended = BookingDto.fromEntity(bookingRepository.save(booking));
        log.info("Extended booking id={} from {} to {} nights", id, oldNights, extended.nights());
        return extended;
    }

    private void lockRoomOrThrow(Long roomId) {
        if (bookingRepository.lockRoom(roomId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Номер не найден: " + roomId);
        }
    }
}
