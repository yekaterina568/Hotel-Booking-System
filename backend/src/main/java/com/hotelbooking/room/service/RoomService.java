package com.hotelbooking.room.service;

import com.hotelbooking.room.db.RoomEntity;
import com.hotelbooking.room.db.RoomRepository;
import com.hotelbooking.room.model.RoomDto;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
public class RoomService {

    private final RoomRepository roomRepository;

    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Cacheable("rooms")
    public List<RoomDto> findAll() {
        return roomRepository.findAll().stream().map(RoomDto::fromEntity).toList();
    }

    @Cacheable("rooms")
    public RoomDto findById(Long id) {
        return RoomDto.fromEntity(findRoomEntityById(id));
    }

    private RoomEntity findRoomEntityById(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Room not found: " + id));
    }

    @CacheEvict(value = "rooms", allEntries = true)
    public RoomDto create(RoomEntity room) {
        return RoomDto.fromEntity(roomRepository.save(room));
    }

    @CacheEvict(value = "rooms", allEntries = true)
    public void delete(Long id) {
        if (!roomRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found: " + id);
        }
        roomRepository.deleteById(id);
    }
}