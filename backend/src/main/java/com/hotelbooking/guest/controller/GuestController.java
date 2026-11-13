package com.hotelbooking.guest.controller;

import com.hotelbooking.guest.db.GuestEntity;
import com.hotelbooking.guest.model.GuestDto;
import com.hotelbooking.guest.service.GuestService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/guests")
public class GuestController {

    private final GuestService guestService;

    public GuestController(GuestService guestService) {
        this.guestService = guestService;
    }

    @GetMapping
    public List<GuestDto> getAll() {
        return guestService.findAll();
    }

    @GetMapping("/{id}")
    public GuestDto getById(@PathVariable Long id) {
        return guestService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GuestDto create(@RequestBody GuestEntity guest) {
        return guestService.create(guest);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        guestService.delete(id);
    }
}