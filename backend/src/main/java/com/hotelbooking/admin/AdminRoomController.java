package com.hotelbooking.admin;

import com.hotelbooking.room.model.RoomDto;
import com.hotelbooking.room.service.RoomService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/rooms")
public class AdminRoomController {
    private final AdminRoomService adminRooms;
    private final RoomService rooms;

    AdminRoomController(AdminRoomService adminRooms, RoomService rooms) {
        this.adminRooms = adminRooms;
        this.rooms = rooms;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomDto create(@Valid @RequestBody AdminRoomRequest request) {
        return adminRooms.create(request);
    }

    @PutMapping("/{id}")
    public RoomDto update(@PathVariable long id, @Valid @RequestBody AdminRoomRequest request) {
        return adminRooms.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        rooms.delete(id);
    }
}
