package com.hotelbooking.guest.db;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "guests")
public class GuestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "guest_id")
    Long guestId;

    @Column(name = "passport_num")
    String passportNum;

    @Column(name = "first_name")
    String firstName;

    @Column(name = "last_name")
    String lastName;

    @Column(name = "phone_num")
    String phoneNum;

    @Column(name = "created_at")
    OffsetDateTime createdAt;
}
