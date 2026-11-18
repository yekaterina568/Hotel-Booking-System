package com.hotelbooking.hotel.controller;

import com.hotelbooking.hotel.model.HotelDto;
import com.hotelbooking.hotel.model.HotelRequest;
import com.hotelbooking.hotel.service.HotelService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/hotels")
public class HotelAdminController {
    private final HotelService hotels;

    public HotelAdminController(HotelService hotels) {
        this.hotels = hotels;
    }

    @GetMapping
    public List<HotelDto> findAll() {
        return hotels.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HotelDto create(@RequestBody @Valid HotelRequest request) {
        return hotels.create(request);
    }

    @PutMapping("/{id}")
    public HotelDto update(@PathVariable Long id, @RequestBody @Valid HotelRequest request) {
        return hotels.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        hotels.delete(id);
    }
}
