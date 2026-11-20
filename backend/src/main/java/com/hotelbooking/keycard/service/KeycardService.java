package com.hotelbooking.keycard.service;

import com.hotelbooking.keycard.db.KeycardEntity;
import com.hotelbooking.keycard.db.KeycardRepository;
import com.hotelbooking.keycard.model.KeycardDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Slf4j
public class KeycardService {

    private final KeycardRepository keycardRepository;

    public KeycardService(KeycardRepository keycardRepository) {
        this.keycardRepository = keycardRepository;
    }

    @Cacheable("keycards")
    public List<KeycardDto> findAll() {
        return keycardRepository.findAll().stream().map(KeycardDto::fromEntity).toList();
    }

    @Cacheable("keycards")
    public KeycardDto findById(Long id) {
        return KeycardDto.fromEntity(findKeycardEntityById(id));
    }

    private KeycardEntity findKeycardEntityById(Long id) {
        return keycardRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Keycard not found: " + id));
    }

    @CacheEvict(value = "keycards", allEntries = true)
    public KeycardDto create(KeycardEntity keycard) {
        KeycardDto created = KeycardDto.fromEntity(keycardRepository.save(keycard));
        log.info("Created keycard id={}", created.keycardId());
        return created;
    }

    @CacheEvict(value = "keycards", allEntries = true)
    public void delete(Long id) {
        if (!keycardRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Keycard not found: " + id);
        }
        keycardRepository.deleteById(id);
        log.info("Deleted keycard id={}", id);
    }
}