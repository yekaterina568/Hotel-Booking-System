package com.hotelbooking.refund.db;

import com.hotelbooking.booking.db.BookingEntity;
import com.hotelbooking.refund.model.RefundState;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "refunds")
public class RefundEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refund_id")
    Long refundId;

    @ManyToOne
    @JoinColumn(name = "booking_id")
    BookingEntity booking;

    @Column(name = "amount")
    BigDecimal amount;

    @Column(name = "refund_reason")
    String refundReason;

    @Column(name = "processed_at")
    OffsetDateTime processedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "state")
    RefundState state;
}
