package com.hotelbooking.booking.db;

import java.time.OffsetDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.hotelbooking.booking.model.BookingState;
import com.hotelbooking.guest.db.GuestEntity;
import com.hotelbooking.room.db.RoomEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
@Entity
@Table(name = "bookings")
public class BookingEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_id")
    Long bookingId;

    @ManyToOne
    @JoinColumn(name = "guest_id")
    GuestEntity guest;

    @ManyToOne
    @JoinColumn(name = "room_id")
    RoomEntity room;

    @Column(name = "expected_arrival")
    OffsetDateTime expectedArrival;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "state")
    BookingState state;

    @Column(name = "created_at")
    OffsetDateTime createdAt;
}
