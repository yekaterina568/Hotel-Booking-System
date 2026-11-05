package com.hotelbooking.keycard.db;

import com.hotelbooking.booking.db.BookingEntity;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "keycards")
public class KeycardEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "keycard_id")
    Long keycardId;

    @ManyToOne
    @JoinColumn(name = "booking_id")
    BookingEntity booking;

    @Column(name = "card_uid")
    String cardUid;

    @Column(name = "issue_timestamp")
    OffsetDateTime issueTimestamp;

    @Column(name = "is_active")
    Boolean isActive;
}
