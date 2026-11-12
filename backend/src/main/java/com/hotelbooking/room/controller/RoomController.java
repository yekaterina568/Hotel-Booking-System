package com.hotelbooking.room.controller;
import static com.hotelbooking.api.ApiDtos.*;
import com.hotelbooking.room.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/rooms") @RequiredArgsConstructor
public class RoomController {
    private final RoomService service;

    @GetMapping public List<RoomResponse> getAll() { return service.findAll(); }
    @GetMapping("/{id}") public RoomResponse getById(@PathVariable Long id) { return service.findById(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public RoomResponse create(@Valid @RequestBody RoomRequest request) { return service.create(request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id) { service.delete(id); }
    @PatchMapping("/{id}") public RoomResponse update(@PathVariable Long id, @Valid @RequestBody RoomUpdate request) { return service.update(id,request); }

}
