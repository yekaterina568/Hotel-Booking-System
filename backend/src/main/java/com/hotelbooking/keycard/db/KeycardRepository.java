package com.hotelbooking.keycard.db;

import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigInteger;

public interface KeycardRepository extends JpaRepository<KeycardEntity, BigInteger> {
}
