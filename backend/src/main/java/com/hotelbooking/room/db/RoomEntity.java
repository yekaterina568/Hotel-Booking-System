package com.hotelbooking.room.db;

import com.hotelbooking.room.model.CleaningState;
import com.hotelbooking.room.model.RoomType;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Entity
@Table(name = "rooms")
public class RoomEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_id")
    Long roomId;

    @Column(name = "room_number")
    Integer roomNumber;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "room_type")
    RoomType roomType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "cleaning_state")
    CleaningState cleaningState;

    @Column(name = "price_per_night")
    BigDecimal pricePerNight;
}
