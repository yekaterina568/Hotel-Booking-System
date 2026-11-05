package com.hotelbooking.booking.archive.db;

import com.hotelbooking.booking.model.BookingState;
import com.hotelbooking.guest.db.GuestEntity;
import com.hotelbooking.room.db.RoomEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;

@Entity
@Table(name = "bookings_archive")
public class BookingArchiveEntity {

    @Id
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

    @Column(name = "archived_at")
    OffsetDateTime archivedAt;
}