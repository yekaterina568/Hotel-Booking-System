package com.hotelbooking.guest.service;

import com.hotelbooking.guest.db.GuestEntity;
import com.hotelbooking.guest.db.GuestRepository;
import com.hotelbooking.guest.model.GuestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Guest registration: normalisation, validation and the unique passport check. */
@ExtendWith(MockitoExtension.class)
class GuestRegistrationTest {

    @Mock
    private GuestRepository guestRepository;

    private GuestService guestService;

    @BeforeEach
    void setUp() {
        guestService = new GuestService(guestRepository);
    }

    private GuestEntity request(String passport, String phone) {
        GuestEntity g = new GuestEntity();
        g.setFirstName("  Aruzhan ");
        g.setLastName("Tanatar");
        g.setPassportNum(passport);
        g.setPhoneNum(phone);
        return g;
    }

    @Test
    @DisplayName("95. Registration normalises input: passport upper case, phone without spaces")
    void create_normalisesAndSaves() {
        when(guestRepository.findByPassportNum("N1234567")).thenReturn(Optional.empty());
        when(guestRepository.save(any(GuestEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        GuestDto result = guestService.create(request(" n1234567 ", "+7 (700) 123-45-67"));

        assertEquals("N1234567", result.passportNum());
        assertEquals("Aruzhan", result.firstName());
        verify(guestRepository).save(argThat(g -> "+77001234567".equals(g.getPhoneNum()) && g.getCreatedAt() != null));
    }

    @Test
    @DisplayName("96. A passport that is already registered gives 409 and nothing is saved")
    void create_duplicatePassport_returns409() {
        GuestEntity existing = new GuestEntity();
        existing.setGuestId(3L);
        when(guestRepository.findByPassportNum("N1234567")).thenReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> guestService.create(request("n1234567", "+77001234567")));

        assertEquals(409, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("ID 3"));
        verify(guestRepository, never()).save(any());
    }

    @Test
    @DisplayName("97. Passport with wrong characters or length gives 400 and doesn't touch the database")
    void create_badPassport_returns400() {
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> guestService.create(request("AB-12", "+77001234567"))).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> guestService.create(request("ПАСПОРТ123", "+77001234567"))).getStatusCode().value());
        verifyNoInteractions(guestRepository);
    }

    @Test
    @DisplayName("98. Missing or too long phone gives 400 (the column holds 15 characters)")
    void create_badPhone_returns400() {
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> guestService.create(request("N1234567", null))).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> guestService.create(request("N1234567", "+7700123456789012"))).getStatusCode().value());
        verifyNoInteractions(guestRepository);
    }
}
