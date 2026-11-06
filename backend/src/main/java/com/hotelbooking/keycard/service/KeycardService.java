package com.hotelbooking.keycard.service;

import com.hotelbooking.keycard.db.KeycardEntity;
import com.hotelbooking.keycard.db.KeycardRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class KeycardService {

    private final KeycardRepository keycardRepository;

    public KeycardService(KeycardRepository keycardRepository) {
        this.keycardRepository = keycardRepository;
    }

    public List<KeycardEntity> findAll() {
        return keycardRepository.findAll();
    }

    public KeycardEntity findById(Long id) {
        return keycardRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Keycard not found: " + id));
    }

    public KeycardEntity create(KeycardEntity keycard) {
        return keycardRepository.save(keycard);
    }

    public void delete(Long id) {
        if (!keycardRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Keycard not found: " + id);
        }
        keycardRepository.deleteById(id);
    }
}