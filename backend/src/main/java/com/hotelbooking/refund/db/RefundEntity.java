package com.hotelbooking.refund.db;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.hotelbooking.booking.db.BookingEntity;
import com.hotelbooking.payment.db.PaymentEntity;
import com.hotelbooking.refund.model.RefundState;
@Entity @Table(name = "refunds") @Getter @Setter
public class RefundEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "refund_id") private Long refundId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "booking_id") private BookingEntity booking;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "payment_id") private PaymentEntity payment;
    @Column(name = "amount") private BigDecimal amount;
    @Column(name = "refund_reason") private String refundReason;
    @Column(name = "processed_at") private OffsetDateTime processedAt;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM) @Column(name = "state", columnDefinition = "refund_state_enum") private RefundState state;
    @Column(name = "idempotency_key") private String idempotencyKey;
}
