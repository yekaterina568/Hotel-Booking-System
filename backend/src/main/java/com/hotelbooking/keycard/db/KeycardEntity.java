package com.hotelbooking.keycard.db;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.hotelbooking.booking.db.BookingEntity;
@Entity @Table(name = "keycards") @Getter @Setter
public class KeycardEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "keycard_id") private Long keycardId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "booking_id") private BookingEntity booking;
    @Column(name = "card_uid") private String cardUid;
    @Column(name = "issue_timestamp") private OffsetDateTime issueTimestamp;
    @Column(name = "is_active") private Boolean isActive;
}
