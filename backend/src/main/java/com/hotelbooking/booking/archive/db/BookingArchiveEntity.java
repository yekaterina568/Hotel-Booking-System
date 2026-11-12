package com.hotelbooking.booking.archive.db;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.hotelbooking.guest.db.GuestEntity;
import com.hotelbooking.room.db.RoomEntity;
import com.hotelbooking.booking.model.BookingState;
@Entity @Table(name = "bookings_archive") @Getter @Setter
public class BookingArchiveEntity {
    @Id @Column(name = "booking_id") private Long bookingId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "guest_id") private GuestEntity guest;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "room_id") private RoomEntity room;
    @Column(name = "expected_arrival") private OffsetDateTime expectedArrival;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM) @Column(name = "state", columnDefinition = "booking_state_enum") private BookingState state;
    @Column(name = "created_at") private OffsetDateTime createdAt;
    @Column(name = "archived_at") private OffsetDateTime archivedAt;
    @Column(name = "check_in_date") private LocalDate checkInDate;
    @Column(name = "check_out_date") private LocalDate checkOutDate;
}
