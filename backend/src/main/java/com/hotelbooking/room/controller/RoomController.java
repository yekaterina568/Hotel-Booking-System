package com.hotelbooking.room.controller;

import com.hotelbooking.booking.service.AvailabilityService;
import com.hotelbooking.room.db.RoomEntity;
import com.hotelbooking.room.model.RoomDto;
import com.hotelbooking.room.model.RoomType;
import com.hotelbooking.room.service.RoomService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;
    private final AvailabilityService availabilityService;

    public RoomController(RoomService roomService, AvailabilityService availabilityService) {
        this.roomService = roomService;
        this.availabilityService = availabilityService;
    }

    @GetMapping
    public List<RoomDto> getAll() {
        return roomService.findAll();
    }

    /** Rooms with no active booking between {@code from} and {@code to}, e.g. /api/rooms/available?from=2026-11-20T14:00:00Z&to=2026-11-22T12:00:00Z */
    @GetMapping("/available")
    public List<RoomDto> getAvailable(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(value = "roomType", required = false) RoomType roomType) {
        return availabilityService.findAvailableRooms(from, to, roomType);
    }

    @GetMapping("/{id}")
    public RoomDto getById(@PathVariable Long id) {
        return roomService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomDto create(@RequestBody RoomEntity room) {
        return roomService.create(room);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        roomService.delete(id);
    }
}