package com.hotelbooking.room.service;

import com.hotelbooking.room.db.RoomEntity;
import com.hotelbooking.room.db.RoomRepository;
import com.hotelbooking.room.model.CleaningState;
import com.hotelbooking.room.model.RoomDto;
import com.hotelbooking.room.model.RoomType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    private RoomService roomService;

    @BeforeEach
    void setUp() {
        roomService = new RoomService(roomRepository);
    }

    @Test
    @DisplayName("15. Creating a room saves it and returns its data as a DTO")
    void create_savesRoomAndReturnsDto() {
        RoomEntity room = new RoomEntity();
        room.setRoomNumber(101);
        room.setRoomType(RoomType.SINGLE);
        room.setCleaningState(CleaningState.CLEANED);
        room.setPricePerNight(new BigDecimal("100.00"));
        when(roomRepository.save(any(RoomEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RoomDto result = roomService.create(room);

        assertEquals(101, (int) result.roomNumber());
        assertEquals(RoomType.SINGLE, result.roomType());
        assertEquals(CleaningState.CLEANED, result.cleaningState());
        assertEquals(new BigDecimal("100.00"), result.pricePerNight());
        verify(roomRepository).save(room);
    }

    @Test
    @DisplayName("16. Deleting an existing room calls deleteById")
    void delete_existingRoom_deletesIt() {
        when(roomRepository.existsById(3L)).thenReturn(true);

        roomService.delete(3L);

        verify(roomRepository).deleteById(3L);
    }
}
