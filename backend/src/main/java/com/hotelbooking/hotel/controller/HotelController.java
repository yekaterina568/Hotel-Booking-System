package com.hotelbooking.hotel.controller;
import static com.hotelbooking.api.ApiDtos.*;
import com.hotelbooking.hotel.service.HotelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/hotels") @RequiredArgsConstructor
public class HotelController {
    private final HotelService service;

    @GetMapping public List<HotelResponse> getAll() { return service.findAll(); }
    @GetMapping("/{id}") public HotelResponse getById(@PathVariable Long id) { return service.findById(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public HotelResponse create(@Valid @RequestBody HotelRequest request) { return service.create(request); }

}
