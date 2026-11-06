package com.hotelbooking.keycard.db;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KeycardRepository extends JpaRepository<KeycardEntity, Long> {
}
