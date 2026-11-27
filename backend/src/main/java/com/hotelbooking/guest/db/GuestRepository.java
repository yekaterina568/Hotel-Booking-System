package com.hotelbooking.guest.db;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GuestRepository extends JpaRepository<GuestEntity, Long> {

    /** Passport numbers are stored in upper case, so an exact match is enough. */
    Optional<GuestEntity> findByPassportNum(String passportNum);
}
