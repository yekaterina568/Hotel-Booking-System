package com.hotelbooking.booking.archive.db;

import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigInteger;

public interface BookingArchiveRepository extends JpaRepository<BookingArchiveEntity, BigInteger> {
}
