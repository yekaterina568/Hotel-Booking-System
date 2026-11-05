package com.hotelbooking.refund.db;

import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.math.BigInteger;

public interface RefundRepository extends JpaRepository<RefundEntity, BigInteger> {
}
