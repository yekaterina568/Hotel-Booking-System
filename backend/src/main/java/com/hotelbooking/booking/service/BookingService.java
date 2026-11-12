package com.hotelbooking.booking.service;
import com.hotelbooking.api.*;
import static com.hotelbooking.api.ApiDtos.*;
import static com.hotelbooking.api.ApiErrors.*;
import com.hotelbooking.booking.db.*;
import com.hotelbooking.booking.model.BookingState;
import com.hotelbooking.room.db.*;
import com.hotelbooking.room.model.CleaningState;
import com.hotelbooking.guest.db.GuestRepository;
import com.hotelbooking.keycard.service.KeycardService;
import com.hotelbooking.cost.service.CostService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class BookingService {
    private final BookingRepository bookings;
    private final RoomRepository rooms;
    private final GuestRepository guests;
    private final KeycardService keycards;
    private final CostService costs;
    public List<BookingResponse> findAll() { return bookings.findAll().stream().map(Views::booking).toList(); }
    public BookingResponse findById(Long id) { return Views.booking(entity(id)); }
    private BookingEntity entity(Long id) { return bookings.findById(id).orElseThrow(() -> missing("Booking")); }
    public static void dates(LocalDate start,LocalDate end) {
        if(start==null || end==null || !end.isAfter(start) || ChronoUnit.DAYS.between(start,end)>365) throw invalid("Stay must be between 1 and 365 nights");
    }
    public AvailabilityResponse availability(Long roomId,LocalDate start,LocalDate end) {
        dates(start,end); if(!rooms.existsById(roomId)) throw missing("Room");
        return new AvailabilityResponse(roomId,start,end,bookings.countConflicts(roomId,start,end)==0);
    }
    @Transactional public BookingResponse create(BookingRequest r) {
        dates(r.checkInDate(),r.checkOutDate());
        var room=rooms.lockById(r.roomId()).orElseThrow(() -> missing("Room"));
        var zone=ZoneId.of(room.getHotel().getTimezone());
        if(r.checkInDate().isBefore(LocalDate.now(zone))) throw invalid("Check-in date cannot be in the past");
        if(!r.expectedArrival().atZoneSameInstant(zone).toLocalDate().equals(r.checkInDate())) throw invalid("Expected arrival must be on the check-in date in the hotel's timezone");
        if(bookings.countConflicts(r.roomId(),r.checkInDate(),r.checkOutDate())>0) throw conflict("Room is already booked for these dates");
        var e=new BookingEntity(); e.setGuest(guests.findById(r.guestId()).orElseThrow(() -> missing("Guest"))); e.setRoom(room);
        e.setExpectedArrival(r.expectedArrival()); e.setCheckInDate(r.checkInDate()); e.setCheckOutDate(r.checkOutDate());
        e.setState(BookingState.CONFIRMED); e.setCreatedAt(OffsetDateTime.now()); bookings.saveAndFlush(e); costs.snapshot(e);
        return Views.booking(e);
    }
    /** All state transitions acquire room then booking locks, in this order. */
    public BookingEntity locked(Long id) {
        var first=entity(id); rooms.lockById(first.getRoom().getRoomId()).orElseThrow(() -> missing("Room"));
        var booking=bookings.lockById(id).orElseThrow(() -> missing("Booking"));
        // No entity is modified before these locks. Refresh avoids an earlier read
        // returning stale state after another transaction won the room lock.
        entityManager.refresh(booking);
        return booking;
    }
    private final jakarta.persistence.EntityManager entityManager;
    @Transactional public BookingResponse checkIn(Long id) {
        var e=locked(id);
        if(e.getState()!=BookingState.CONFIRMED) throw conflict("Only a confirmed booking can check in");
        entityManager.refresh(e.getRoom());
        if(e.getRoom().getCleaningState()!=CleaningState.CLEANED) throw conflict("Room must be Cleaned before check-in");
        var today=LocalDate.now(ZoneId.of(e.getRoom().getHotel().getTimezone()));
        if(e.getCheckInDate()==null || today.isBefore(e.getCheckInDate()) || !today.isBefore(e.getCheckOutDate())) throw conflict("Booking is outside its dated stay; legacy dates need staff review");
        // Also block an unclosed historical stay in the same physical room.
        boolean occupied=bookings.findByStateOrderByExpectedArrivalAscBookingIdAsc(BookingState.CHECKEDIN).stream()
            .anyMatch(b -> b.getRoom().getRoomId().equals(e.getRoom().getRoomId()));
        if(occupied) throw conflict("Room still has a checked-in guest");
        e.setState(BookingState.CHECKEDIN); e.setCheckedInAt(OffsetDateTime.now()); keycards.issue(e);
        return Views.booking(e);
    }
    @Transactional public BookingResponse cancel(Long id) {
        var e=locked(id); if(e.getState()!=BookingState.CONFIRMED) throw conflict("Only a confirmed booking can be cancelled");
        e.setState(BookingState.CANCELLED); keycards.deactivate(id); return Views.booking(e);
    }
    public List<BookingResponse> priorityQueue() {
        return bookings.findByStateOrderByExpectedArrivalAscBookingIdAsc(BookingState.CONFIRMED).stream().filter(e -> {
            var today=LocalDate.now(ZoneId.of(e.getRoom().getHotel().getTimezone()));
            return e.getRoom().getCleaningState()==CleaningState.CLEANED && e.getCheckInDate()!=null
                && !today.isBefore(e.getCheckInDate()) && today.isBefore(e.getCheckOutDate())
                && bookings.findByStateOrderByExpectedArrivalAscBookingIdAsc(BookingState.CHECKEDIN).stream()
                    .noneMatch(b -> b.getRoom().getRoomId().equals(e.getRoom().getRoomId()));
        }).map(Views::booking).toList();
    }
}
