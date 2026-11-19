package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.db.HotelEntity;
import com.hotelbooking.hotel.db.HotelRepository;
import com.hotelbooking.hotel.model.HotelDto;
import com.hotelbooking.hotel.model.HotelRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class HotelService {
    private final HotelRepository hotels;

    public HotelService(HotelRepository hotels) {
        this.hotels = hotels;
    }

    public List<HotelDto> findAll() {
        return hotels.findAll(Sort.by("name", "hotelId")).stream().map(HotelDto::fromEntity).toList();
    }

    @Transactional
    public HotelDto create(HotelRequest request) {
        HotelEntity hotel = new HotelEntity();
        apply(hotel, request);
        return HotelDto.fromEntity(hotels.save(hotel));
    }

    @Transactional
    public HotelDto update(Long id, HotelRequest request) {
        HotelEntity hotel = findHotel(id);
        apply(hotel, request);
        return HotelDto.fromEntity(hotel);
    }

    @Transactional
    public void delete(Long id) {
        if (id == HotelEntity.DEFAULT_HOTEL_ID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The default hotel is used for room bookings.");
        }
        HotelEntity hotel = findHotel(id);
        try {
            hotels.delete(hotel);
            hotels.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This hotel still has rooms or reviews.", exception);
        }
    }

    public HotelEntity findHotel(Long id) {
        return hotels.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Hotel not found."));
    }

    private void apply(HotelEntity hotel, HotelRequest request) {
        hotel.setName(request.name().strip());
        hotel.setCity(request.city().strip());
        hotel.setAddress(request.address() == null ? null : request.address().strip());
        hotel.setDescription(request.description() == null ? null : request.description().strip());
    }
}
