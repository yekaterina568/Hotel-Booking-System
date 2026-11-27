package com.hotelbooking.guest.service;

import com.hotelbooking.guest.db.GuestEntity;
import com.hotelbooking.guest.db.GuestRepository;
import com.hotelbooking.guest.model.GuestDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.regex.Pattern;

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

    /** Same rule as the database CHECK constraint chk_passport_format. */
    private static final Pattern PASSPORT = Pattern.compile("^[A-Z0-9]{6,20}$");
    /** Digits with an optional leading +; the column is VARCHAR(15). */
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{7,14}$");

    /**
     * Registers a guest. Input is normalised (trimmed names, upper-case passport, phone without spaces,
     * dashes or brackets) and checked before the insert, so the user gets a clear 400/409 instead of a 500.
     */
    @Transactional
    @CacheEvict(value = "guests", allEntries = true)
    public GuestDto create(GuestEntity guest) {
        String firstName = trim(guest.getFirstName());
        String lastName = trim(guest.getLastName());
        String passport = guest.getPassportNum() == null ? "" : guest.getPassportNum().replaceAll("\\s", "").toUpperCase();
        String phone = guest.getPhoneNum() == null ? "" : guest.getPhoneNum().replaceAll("[\\s()\\-]", "");

        if (firstName.isEmpty() || lastName.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите имя и фамилию");
        }
        if (firstName.length() > 30 || lastName.length() > 30) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Имя и фамилия — не длиннее 30 символов");
        }
        if (!PASSPORT.matcher(passport).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Номер паспорта: от 6 до 20 латинских букв и цифр, без пробелов и знаков");
        }
        if (!PHONE.matcher(phone).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Телефон: от 7 до 14 цифр, можно с + в начале (например +77001234567)");
        }
        guestRepository.findByPassportNum(passport).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Гость с паспортом " + passport + " уже зарегистрирован (ID " + existing.getGuestId() + ")");
        });

        guest.setGuestId(null);
        guest.setFirstName(firstName);
        guest.setLastName(lastName);
        guest.setPassportNum(passport);
        guest.setPhoneNum(phone);
        if (guest.getCreatedAt() == null) {
            guest.setCreatedAt(OffsetDateTime.now());
        }
        GuestDto created = GuestDto.fromEntity(guestRepository.save(guest));
        log.info("Created guest id={}", created.guestId());
        return created;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
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