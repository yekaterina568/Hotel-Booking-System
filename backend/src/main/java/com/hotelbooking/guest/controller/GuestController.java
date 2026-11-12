package com.hotelbooking.guest.controller;
import static com.hotelbooking.api.ApiDtos.*;
import com.hotelbooking.guest.service.GuestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/guests") @RequiredArgsConstructor
public class GuestController {
    private final GuestService service;

    @GetMapping public List<GuestResponse> getAll() { return service.findAll(); }
    @GetMapping("/{id}") public GuestResponse getById(@PathVariable Long id) { return service.findById(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public GuestResponse create(@Valid @RequestBody GuestRequest request) { return service.create(request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id) { service.delete(id); }

}
