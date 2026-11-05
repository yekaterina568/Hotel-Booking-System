package com.hotelbooking.booking.db;

import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigInteger;

public interface BookingRepository extends JpaRepository<BookingEntity, BigInteger> {
}
