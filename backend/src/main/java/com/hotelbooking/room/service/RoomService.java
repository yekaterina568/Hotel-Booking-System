package com.hotelbooking.room.service;
import com.hotelbooking.api.*;
import static com.hotelbooking.api.ApiDtos.*;
import static com.hotelbooking.api.ApiErrors.*;
import com.hotelbooking.room.db.*;
import com.hotelbooking.hotel.db.HotelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.*;
import java.util.List;
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class RoomService {
    private final RoomRepository rooms;
    private final HotelRepository hotels;
    @Cacheable("rooms")
    public List<RoomResponse> findAll() { return rooms.findAll().stream().map(Views::room).toList(); }
    public RoomResponse findById(Long id) { return Views.room(rooms.findById(id).orElseThrow(() -> missing("Room"))); }
    @Transactional @CacheEvict(value="rooms",allEntries=true)
    public RoomResponse create(RoomRequest r) {
        var e=new RoomEntity(); e.setHotel(hotels.findById(r.hotelId()).orElseThrow(() -> missing("Hotel")));
        e.setRoomNumber(r.roomNumber()); e.setRoomType(r.roomType()); e.setCleaningState(r.cleaningState()); e.setPricePerNight(r.pricePerNight());
        return Views.room(rooms.save(e));
    }
    @Transactional @CacheEvict(value="rooms",allEntries=true)
    public RoomResponse update(Long id, RoomUpdate r) {
        var e=rooms.lockById(id).orElseThrow(() -> missing("Room"));
        e.setCleaningState(r.cleaningState()); if(r.pricePerNight()!=null) e.setPricePerNight(r.pricePerNight());
        return Views.room(e);
    }
    @Transactional @CacheEvict(value="rooms",allEntries=true)
    public void delete(Long id) { rooms.delete(rooms.lockById(id).orElseThrow(() -> missing("Room"))); rooms.flush(); }
}
