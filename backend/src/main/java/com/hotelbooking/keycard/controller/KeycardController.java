package com.hotelbooking.keycard.controller;

import com.hotelbooking.keycard.db.KeycardEntity;
import com.hotelbooking.keycard.model.KeycardDto;
import com.hotelbooking.keycard.service.KeycardService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;
import java.util.List;

@RestController
@RequestMapping("/api/keycards")
public class KeycardController {

    private final KeycardService keycardService;

    public KeycardController(KeycardService keycardService) {
        this.keycardService = keycardService;
    }

    @GetMapping
    public List<KeycardDto> getAll() {
        return keycardService.findAll();
    }

    @GetMapping("/{id}")
    public KeycardDto getById(@PathVariable Long id) {
        return keycardService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KeycardDto create(@RequestBody KeycardEntity keycard) {
        return keycardService.create(keycard);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        keycardService.delete(id);
    }
}