package com.hotelbooking.keycard.service;

import com.hotelbooking.booking.db.BookingEntity;
import com.hotelbooking.keycard.db.KeycardEntity;
import com.hotelbooking.keycard.db.KeycardRepository;
import com.hotelbooking.keycard.model.KeycardDto;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** Unit tests for keycards. The repository is mocked, so no database is needed. */
@ExtendWith(MockitoExtension.class)
class KeycardServiceTest {

    @Mock
    private KeycardRepository keycardRepository;

    private KeycardService keycardService;

    @BeforeEach
    void setUp() {
        keycardService = new KeycardService(keycardRepository);
    }

    @Test
    @DisplayName("34. Issuing a keycard saves it and the DTO shows the booking, card UID and active flag")
    void create_savesKeycardAndReturnsDto() {
        BookingEntity booking = new BookingEntity();
        booking.setBookingId(5L);
        KeycardEntity card = new KeycardEntity();
        card.setBooking(booking);
        card.setCardUid("RFID-0001");
        card.setIsActive(true);
        when(keycardRepository.save(any(KeycardEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        KeycardDto result = keycardService.create(card);

        assertEquals(5L, (long) result.bookingId());
        assertEquals("RFID-0001", result.cardUid());
        assertTrue(result.isActive());
        verify(keycardRepository).save(card);
    }

    @Test
    @DisplayName("35. Unknown keycard id gives 404")
    void findById_unknownKeycard_returns404() {
        when(keycardRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> keycardService.findById(99L));

        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("36. Deleting an unknown keycard gives 404 and deletes nothing")
    void delete_unknownKeycard_returns404() {
        when(keycardRepository.existsById(7L)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> keycardService.delete(7L));

        assertEquals(404, ex.getStatusCode().value());
        verify(keycardRepository, never()).deleteById(anyLong());
    }
}
