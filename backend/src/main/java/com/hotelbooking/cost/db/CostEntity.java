package com.hotelbooking.cost.db;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.hotelbooking.booking.db.BookingEntity;
@Entity @Table(name = "booking_costs") @Getter @Setter
public class CostEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "cost_id") private Long costId;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "booking_id") private BookingEntity booking;
    @Column(name = "nights") private Integer nights;
    @Column(name = "nightly_rate") private BigDecimal nightlyRate;
    @Column(name = "subtotal") private BigDecimal subtotal;
    @Column(name = "service_fee") private BigDecimal serviceFee;
    @Column(name = "total") private BigDecimal total;
    @Column(name = "currency") private String currency;
    @Column(name = "estimated_legacy") private boolean estimatedLegacy;
}
