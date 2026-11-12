package com.hotelbooking.room.db;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RoomRepository extends JpaRepository<RoomEntity, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from RoomEntity r where r.roomId = :id")
    java.util.Optional<RoomEntity> lockById(Long id);

}
