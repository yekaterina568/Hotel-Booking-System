package com.hotelbooking.guest.service;

import com.hotelbooking.guest.db.GuestEntity;
import com.hotelbooking.guest.db.GuestRepository;
import com.hotelbooking.guest.model.GuestDto;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
public class GuestService {

    private final GuestRepository guestRepository;

    public GuestService(GuestRepository guestRepository) {
        this.guestRepository = guestRepository;
    }

    @Cacheable("guests")
    public List<GuestDto> findAll() {
        return guestRepository.findAll().stream().map(GuestDto::fromEntity).toList();
    }

    @Cacheable("guests")
    public GuestDto findById(Long id) {
        return GuestDto.fromEntity(findGuestEntityById(id));
    }

    private GuestEntity findGuestEntityById(Long id) {
        return guestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Guest not found: " + id));
    }

    @CacheEvict(value = "guests", allEntries = true)
    public GuestDto create(GuestEntity guest) {
        return GuestDto.fromEntity(guestRepository.save(guest));
    }

    @CacheEvict(value = "guests", allEntries = true)
    public void delete(Long id) {
        if (!guestRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Guest not found: " + id);
        }
        guestRepository.deleteById(id);
    }
}