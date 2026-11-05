package com.hotelbooking.room.db;

import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigInteger;

public interface RoomRepository extends JpaRepository<RoomEntity, BigInteger> {
}
