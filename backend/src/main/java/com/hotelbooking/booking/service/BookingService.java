package com.hotelbooking.booking.service;

import com.hotelbooking.booking.db.BookingEntity;
import com.hotelbooking.booking.db.BookingRepository;
import com.hotelbooking.booking.model.BookingDto;
import com.hotelbooking.booking.model.BookingRequest;
import com.hotelbooking.booking.model.BookingState;
import com.hotelbooking.guest.db.GuestEntity;
import com.hotelbooking.guest.db.GuestRepository;
import com.hotelbooking.room.db.RoomEntity;
import com.hotelbooking.room.db.RoomRepository;
import com.hotelbooking.room.model.CleaningState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final GuestRepository guestRepository;
    private final RoomRepository roomRepository;
    private final Clock clock;

    @Autowired
    public BookingService(BookingRepository bookingRepository,
                          GuestRepository guestRepository,
                          RoomRepository roomRepository) {
        this(bookingRepository, guestRepository, roomRepository, Clock.systemDefaultZone());
    }

    // Used by tests to fix "now" to a known moment
    BookingService(BookingRepository bookingRepository,
                   GuestRepository guestRepository,
                   RoomRepository roomRepository,
                   Clock clock) {
        this.bookingRepository = bookingRepository;
        this.guestRepository = guestRepository;
        this.roomRepository = roomRepository;
        this.clock = clock;
    }

    /** All bookings, optionally filtered by state and/or guest. */
    @Cacheable("bookings")
    public List<BookingDto> findAll(BookingState state, Long guestId) {
        List<BookingEntity> bookings;
        if (state != null && guestId != null) {
            bookings = bookingRepository.findByGuest_GuestIdAndState(guestId, state);
        } else if (state != null) {
            bookings = bookingRepository.findByState(state);
        } else if (guestId != null) {
            bookings = bookingRepository.findByGuest_GuestId(guestId);
        } else {
            bookings = bookingRepository.findAll();
        }
        return bookings.stream()
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

    /**
     * Creates a new CONFIRMED booking.
     * Rules: all fields are required, arrival can't be in the past,
     * guest and room must exist, and the room can't have another active
     * booking (CONFIRMED or CHECKEDIN) on the same day.
     */
    @Transactional
    @CacheEvict(value = "bookings", allEntries = true)
    public BookingDto create(BookingRequest request) {
        if (request == null || request.guestId() == null
                || request.roomId() == null || request.expectedArrival() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "guestId, roomId and expectedArrival are required");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        if (request.expectedArrival().isBefore(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Expected arrival can't be in the past");
        }

        GuestEntity guest = guestRepository.findById(request.guestId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Guest not found: " + request.guestId()));
        RoomEntity room = roomRepository.findById(request.roomId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Room not found: " + request.roomId()));

        // "Same day" is counted in the hotel's time zone (the server clock),
        // because JSON and the database may give the time in UTC
        LocalDate arrivalDay = hotelDay(request.expectedArrival());
        boolean roomTaken = bookingRepository.findByRoom_RoomId(room.getRoomId()).stream()
                .filter(b -> b.getState() == BookingState.CONFIRMED
                        || b.getState() == BookingState.CHECKEDIN)
                .anyMatch(b -> hotelDay(b.getExpectedArrival()).equals(arrivalDay));
        if (roomTaken) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Room " + room.getRoomNumber() + " is already booked on " + arrivalDay);
        }

        BookingEntity booking = new BookingEntity();
        booking.setGuest(guest);
        booking.setRoom(room);
        booking.setExpectedArrival(request.expectedArrival());
        booking.setState(BookingState.CONFIRMED);
        booking.setCreatedAt(now);
        return BookingDto.fromEntity(bookingRepository.save(booking));
    }

    private LocalDate hotelDay(OffsetDateTime time) {
        return time.atZoneSameInstant(clock.getZone()).toLocalDate();
    }

    /** Check-in is allowed only for a CONFIRMED booking whose room is already cleaned. */
    @Transactional
    @CacheEvict(value = "bookings", allEntries = true)
    public BookingDto checkIn(Long id) {
        BookingEntity booking = findEntityById(id);
        if (booking.getState() != BookingState.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a confirmed booking can be checked in (current state: " + booking.getState() + ")");
        }
        if (booking.getRoom().getCleaningState() != CleaningState.CLEANED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Room " + booking.getRoom().getRoomNumber() + " is not cleaned yet");
        }
        booking.setState(BookingState.CHECKEDIN);
        return BookingDto.fromEntity(bookingRepository.save(booking));
    }

    @Transactional
    @CacheEvict(value = "bookings", allEntries = true)
    public BookingDto cancel(Long id) {
        BookingEntity booking = findEntityById(id);
        if (booking.getState() != BookingState.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a confirmed booking can be cancelled (current state: " + booking.getState() + ")");
        }
        booking.setState(BookingState.CANCELLED);
        return BookingDto.fromEntity(bookingRepository.save(booking));
    }
}
