package com.hotelbooking.refund.db;

import org.springframework.data.jpa.repository.JpaRepository;


public interface RefundRepository extends JpaRepository<RefundEntity, Long> {
}
