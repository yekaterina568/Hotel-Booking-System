package com.hotelbooking.keycard.controller;
import static com.hotelbooking.api.ApiDtos.*;
import com.hotelbooking.keycard.service.KeycardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/keycards") @RequiredArgsConstructor
public class KeycardController {
    private final KeycardService service;

    @GetMapping public List<KeycardResponse> getAll() { return service.findAll(); }
    @GetMapping("/{id}") public KeycardResponse getById(@PathVariable Long id) { return service.findById(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public KeycardResponse create(@Valid @RequestBody KeycardRequest request) { return service.create(request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id) { service.delete(id); }

}
