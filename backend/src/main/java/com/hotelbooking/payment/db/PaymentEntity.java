package com.hotelbooking.payment.db;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Таблица payments (changeset 002). method/status хранятся как VARCHAR + CHECK,
 * поэтому здесь обычные String (значения см. PaymentMethod / PaymentStatus).
 */
@Getter
@Setter
@Entity
@Table(name = "payments")
public class PaymentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "method")
    private String method;

    @Column(name = "status")
    private String status;

    @Column(name = "transaction_ref")
    private String transactionRef;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;
}
