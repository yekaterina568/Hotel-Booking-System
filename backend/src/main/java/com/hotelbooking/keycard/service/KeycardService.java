package com.hotelbooking.keycard.service;
import com.hotelbooking.api.*;
import static com.hotelbooking.api.ApiDtos.*;
import static com.hotelbooking.api.ApiErrors.*;
import com.hotelbooking.keycard.db.*;
import com.hotelbooking.booking.db.*;
import com.hotelbooking.room.db.RoomRepository;
import com.hotelbooking.booking.model.BookingState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class KeycardService {
    private final KeycardRepository keycards;
    private final BookingRepository bookings;
    private final RoomRepository rooms;
    private final jakarta.persistence.EntityManager entityManager;
    public List<KeycardResponse> findAll() { return keycards.findAll().stream().map(Views::keycard).toList(); }
    public KeycardResponse findById(Long id) { return Views.keycard(keycards.findById(id).orElseThrow(() -> missing("Keycard"))); }
    @Transactional public KeycardResponse create(KeycardRequest r) {
        var first=bookings.findById(r.bookingId()).orElseThrow(() -> missing("Booking"));
        rooms.lockById(first.getRoom().getRoomId()).orElseThrow(() -> missing("Room"));
        var b=bookings.lockById(r.bookingId()).orElseThrow(() -> missing("Booking")); entityManager.refresh(b);
        return Views.keycard(issue(b));
    }
    @Transactional public KeycardEntity issue(BookingEntity b) {
        if(b.getState()!=BookingState.CHECKEDIN) throw conflict("Keycards require a checked-in booking");
        var active=keycards.findByBookingBookingIdAndIsActiveTrue(b.getBookingId()); if(!active.isEmpty()) return active.getFirst();
        var e=new KeycardEntity(); e.setBooking(b); e.setCardUid(UUID.randomUUID().toString().replace("-",""));
        e.setIssueTimestamp(OffsetDateTime.now()); e.setIsActive(true); return keycards.saveAndFlush(e);
    }
    @Transactional public void deactivate(Long bookingId) { keycards.findByBookingBookingIdAndIsActiveTrue(bookingId).forEach(e -> e.setIsActive(false)); }
    @Transactional public void delete(Long id) {
        // Preserve issuance history; DELETE revokes rather than removing the record.
        var card=keycards.findById(id).orElseThrow(() -> missing("Keycard"));
        rooms.lockById(card.getBooking().getRoom().getRoomId()).orElseThrow(() -> missing("Room"));
        entityManager.refresh(card); card.setIsActive(false);
    }
}
