package com.hotelbooking.room.controller;

import com.hotelbooking.room.db.RoomEntity;
import com.hotelbooking.room.dto.CleaningStateRequest;
import com.hotelbooking.room.service.RoomService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping
    public List<RoomEntity> getAll() {
        return roomService.findAll();
    }

    @GetMapping("/{id}")
    public RoomEntity getById(@PathVariable Long id) {
        return roomService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomEntity create(@RequestBody RoomEntity room) {
        return roomService.create(room);
    }

    @PutMapping("/{id}/cleaning-state")
    public RoomEntity updateCleaningState(@PathVariable Long id,
                                          @RequestBody CleaningStateRequest request) {
        return roomService.updateCleaningState(id, request.cleaningState());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        roomService.delete(id);
    }
}