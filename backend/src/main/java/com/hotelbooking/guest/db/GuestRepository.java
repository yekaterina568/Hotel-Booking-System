package com.hotelbooking.guest.db;

import org.springframework.data.jpa.repository.JpaRepository;
public interface GuestRepository extends JpaRepository<GuestEntity, Long> {
}
