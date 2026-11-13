package com.hotelbooking.room.service;

import com.hotelbooking.room.db.RoomEntity;
import com.hotelbooking.room.db.RoomRepository;
import com.hotelbooking.room.model.CleaningState;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.Arrays;
import java.util.List;

@Service
public class RoomService {

    private final RoomRepository roomRepository;

    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    public List<RoomEntity> findAll() {
        return roomRepository.findAll();
    }

    public RoomEntity findById(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Room not found: " + id));
    }

    public RoomEntity create(RoomEntity room) {
        return roomRepository.save(room);
    }

    public void delete(Long id) {
        if (!roomRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found: " + id);
        }
        roomRepository.deleteById(id);
    }

    public RoomEntity updateCleaningState(Long id, String rawState) {
        if (rawState == null || rawState.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Укажите статус уборки: Cleaned, Cleaning или Dirty");
        }
        CleaningState state = Arrays.stream(CleaningState.values())
                .filter(s -> s.name().equalsIgnoreCase(rawState.trim()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Неизвестный статус уборки: " + rawState + " (доступно: Cleaned, Cleaning, Dirty)"));
        RoomEntity room = findById(id);
        room.setCleaningState(state);
        return roomRepository.save(room);
    }
}
