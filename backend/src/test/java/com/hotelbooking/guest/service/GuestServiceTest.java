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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GuestServiceTest {

    @Mock
    private GuestRepository guestRepository;

    private GuestService guestService;

    @BeforeEach
    void setUp() {
        guestService = new GuestService(guestRepository);
    }

    @Test
    @DisplayName("13. findById returns the guest's data as a DTO")
    void findById_existingGuest_returnsDto() {
        GuestEntity guest = new GuestEntity();
        guest.setGuestId(1L);
        guest.setFirstName("Aruzhan");
        guest.setLastName("Tanatar");
        guest.setPassportNum("N1234567");
        when(guestRepository.findById(1L)).thenReturn(Optional.of(guest));

        GuestDto result = guestService.findById(1L);

        assertEquals("Aruzhan", result.firstName());
        assertEquals("Tanatar", result.lastName());
        assertEquals("N1234567", result.passportNum());
    }

    @Test
    @DisplayName("14. Deleting an unknown guest gives 404 and deletes nothing")
    void delete_unknownGuest_returns404() {
        when(guestRepository.existsById(7L)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> guestService.delete(7L));

        assertEquals(404, ex.getStatusCode().value());
        verify(guestRepository, never()).deleteById(anyLong());
    }
}
