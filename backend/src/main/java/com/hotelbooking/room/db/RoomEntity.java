package com.hotelbooking.room.db;

import com.hotelbooking.room.model.CleaningState;
import com.hotelbooking.room.model.RoomType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "rooms")
public class RoomEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_id")
    Long roomId;

    @Generated(event = EventType.INSERT)
    @Column(name = "hotel_id", nullable = false, insertable = false, updatable = false)
    Long hotelId;

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
