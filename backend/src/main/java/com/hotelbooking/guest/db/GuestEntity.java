package com.hotelbooking.guest.db;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name = "guests") @Getter @Setter
public class GuestEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "guest_id") private Long guestId;
    @Column(name = "passport_num") private String passportNum;
    @Column(name = "first_name") private String firstName;
    @Column(name = "last_name") private String lastName;
    @Column(name = "phone_num") private String phoneNum;
    @Column(name = "created_at") private OffsetDateTime createdAt;
}
