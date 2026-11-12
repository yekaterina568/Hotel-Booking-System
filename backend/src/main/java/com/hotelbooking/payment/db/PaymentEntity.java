package com.hotelbooking.payment.db;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.hotelbooking.booking.db.BookingEntity;
@Entity @Table(name = "payments") @Getter @Setter
public class PaymentEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "payment_id") private Long paymentId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "booking_id") private BookingEntity booking;
    @Column(name = "amount") private BigDecimal amount;
    @Column(name = "currency") private String currency;
    @Column(name = "state") private String state;
    @Column(name = "idempotency_key") private String idempotencyKey;
    @Column(name = "created_at") private OffsetDateTime createdAt;
    @Column(name = "provider_reference") private String providerReference;
}
