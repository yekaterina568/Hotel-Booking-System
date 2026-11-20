package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.db.HotelEntity;
import com.hotelbooking.hotel.db.HotelRepository;
import com.hotelbooking.hotel.model.HotelDto;
import com.hotelbooking.hotel.model.HotelRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Unit tests for hotel administration. The repository is mocked, so no database is needed. */
@ExtendWith(MockitoExtension.class)
class HotelServiceTest {

    @Mock
    private HotelRepository hotelRepository;

    private HotelService hotelService;

    @BeforeEach
    void setUp() {
        hotelService = new HotelService(hotelRepository);
    }

    private HotelEntity hotel(long id) {
        HotelEntity h = new HotelEntity();
        h.setHotelId(id);
        h.setName("Grand Almaty");
        h.setCity("Almaty");
        return h;
    }

    @Test
    @DisplayName("40. Creating a hotel trims extra spaces and keeps an empty address empty")
    void create_trimsTextAndSaves() {
        when(hotelRepository.save(any(HotelEntity.class))).thenAnswer(inv -> {
            HotelEntity saved = inv.getArgument(0);
            saved.setHotelId(2L); // the database gives the new hotel an id
            return saved;
        });

        HotelDto result = hotelService.create(
                new HotelRequest("  Grand Almaty  ", " Almaty ", null, "  Near the park "));

        assertEquals("Grand Almaty", result.name());
        assertEquals("Almaty", result.city());
        assertNull(result.address());
        assertEquals("Near the park", result.description());
        assertFalse(result.defaultHotel());
    }

    @Test
    @DisplayName("41. The default hotel can't be deleted (409)")
    void delete_defaultHotel_returns409() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> hotelService.delete(HotelEntity.DEFAULT_HOTEL_ID));

        assertEquals(409, ex.getStatusCode().value());
        verify(hotelRepository, never()).delete(any());
    }

    @Test
    @DisplayName("42. A hotel that still has rooms or reviews can't be deleted (409)")
    void delete_hotelWithRooms_returns409() {
        when(hotelRepository.findById(5L)).thenReturn(Optional.of(hotel(5L)));
        doThrow(new DataIntegrityViolationException("rooms reference this hotel"))
                .when(hotelRepository).flush();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> hotelService.delete(5L));

        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("43. Updating an unknown hotel gives 404")
    void update_unknownHotel_returns404() {
        when(hotelRepository.findById(77L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> hotelService.update(77L, new HotelRequest("Name", "City", null, null)));

        assertEquals(404, ex.getStatusCode().value());
    }
}
