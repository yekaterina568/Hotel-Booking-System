package com.hotelbooking.guest.db;

import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigInteger;

public interface GuestRepository extends JpaRepository<GuestEntity, BigInteger> {
}
