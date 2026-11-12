package com.hotelbooking.room.db;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.hotelbooking.hotel.db.HotelEntity;
import com.hotelbooking.room.model.*;
@Entity @Table(name = "rooms") @Getter @Setter
public class RoomEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "room_id") private Long roomId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "hotel_id") private HotelEntity hotel;
    @Column(name = "room_number") private Integer roomNumber;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM) @Column(name = "room_type", columnDefinition = "room_type_enum") private RoomType roomType;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM) @Column(name = "cleaning_state", columnDefinition = "cleaning_state_enum") private CleaningState cleaningState;
    @Column(name = "price_per_night") private BigDecimal pricePerNight;
}
