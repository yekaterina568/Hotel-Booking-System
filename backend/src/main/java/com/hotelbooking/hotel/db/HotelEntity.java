package com.hotelbooking.hotel.db;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name = "hotels") @Getter @Setter
public class HotelEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "hotel_id") private Long hotelId;
    @Column(name = "name") private String name;
    @Column(name = "city") private String city;
    @Column(name = "timezone") private String timezone;
}
