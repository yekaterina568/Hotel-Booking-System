package com.hotelbooking.hotel.service;
import com.hotelbooking.api.*;
import static com.hotelbooking.api.ApiDtos.*;
import static com.hotelbooking.api.ApiErrors.*;
import com.hotelbooking.hotel.db.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.ZoneId;
import java.util.List;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class HotelService {
    private final HotelRepository hotels;
    public List<HotelResponse> findAll() { return hotels.findAll().stream().map(Views::hotel).toList(); }
    public HotelResponse findById(Long id) { return Views.hotel(hotels.findById(id).orElseThrow(() -> missing("Hotel"))); }
    @Transactional public HotelResponse create(HotelRequest r) {
        try { ZoneId.of(r.timezone()); } catch(java.time.DateTimeException e) { throw invalid("Invalid hotel timezone"); }
        var e=new HotelEntity(); e.setName(r.name()); e.setCity(r.city()); e.setTimezone(r.timezone()); return Views.hotel(hotels.save(e));
    }
}
