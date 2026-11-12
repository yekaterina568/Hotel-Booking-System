package com.hotelbooking.booking.archive.service;
import com.hotelbooking.api.*;
import static com.hotelbooking.api.ApiDtos.*;
import static com.hotelbooking.api.ApiErrors.*;
import com.hotelbooking.booking.archive.db.*;
import com.hotelbooking.booking.service.BookingService;
import com.hotelbooking.booking.model.BookingState;
import com.hotelbooking.room.model.CleaningState;
import com.hotelbooking.keycard.service.KeycardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import java.time.OffsetDateTime;
import java.util.List;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class BookingArchiveService {
    private final BookingArchiveRepository archives;
    private final BookingService bookings;
    private final KeycardService keycards;
    public List<ArchiveResponse> findAll() { return archives.findAll().stream().map(Views::archive).toList(); }
    public ArchiveResponse findById(Long id) { return Views.archive(archives.findById(id).orElseThrow(() -> missing("Archive"))); }
    @Transactional @CacheEvict(value="rooms",allEntries=true)
    public ArchiveResponse archive(Long id) {
        var b=bookings.locked(id); if(b.getState()!=BookingState.CANCELLED && b.getState()!=BookingState.CHECKEDIN) throw conflict("Only cancelled bookings or checked-in stays can be archived");
        if(archives.existsById(id)) throw conflict("Booking is already archived");
        var e=new BookingArchiveEntity(); e.setBookingId(id); e.setGuest(b.getGuest()); e.setRoom(b.getRoom());
        e.setExpectedArrival(b.getExpectedArrival()); e.setCreatedAt(b.getCreatedAt()); e.setState(b.getState());
        e.setCheckInDate(b.getCheckInDate()); e.setCheckOutDate(b.getCheckOutDate()); e.setArchivedAt(OffsetDateTime.now());
        archives.save(e);
        if(b.getState()==BookingState.CHECKEDIN) b.getRoom().setCleaningState(CleaningState.DIRTY);
        b.setState(BookingState.ARCHIVED); keycards.deactivate(id); return Views.archive(e);
    }
}
