package com.hotelbooking.guest.service;

import com.hotelbooking.guest.db.GuestEntity;
import com.hotelbooking.guest.db.GuestRepository;
import com.hotelbooking.guest.model.GuestDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@Slf4j
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
        if (guest.getCreatedAt() == null) {
            guest.setCreatedAt(OffsetDateTime.now());
        }
        GuestDto created = GuestDto.fromEntity(guestRepository.save(guest));
        log.info("Created guest id={}", created.guestId());
        return created;
    }

    @CacheEvict(value = "guests", allEntries = true)
    public void delete(Long id) {
        if (!guestRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Guest not found: " + id);
        }
        guestRepository.deleteById(id);
        log.info("Deleted guest id={}", id);
    }
}