package com.hotelbooking.guest.service;
import com.hotelbooking.api.*;
import static com.hotelbooking.api.ApiDtos.*;
import static com.hotelbooking.api.ApiErrors.*;
import com.hotelbooking.guest.db.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class GuestService {
    private final GuestRepository guests;
    public List<GuestResponse> findAll() { return guests.findAll().stream().map(Views::guest).toList(); }
    public GuestResponse findById(Long id) { return Views.guest(guests.findById(id).orElseThrow(() -> missing("Guest"))); }
    @Transactional public GuestResponse create(GuestRequest r) {
        var e=new GuestEntity(); e.setPassportNum(r.passportNum()); e.setFirstName(r.firstName()); e.setLastName(r.lastName()); e.setPhoneNum(r.phoneNum()); e.setCreatedAt(OffsetDateTime.now());
        return Views.guest(guests.save(e));
    }
    @Transactional public void delete(Long id) { guests.delete(guests.findById(id).orElseThrow(() -> missing("Guest"))); guests.flush(); }
}
