package com.hotelbooking.review.db;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.hotelbooking.booking.db.BookingEntity;
@Entity @Table(name = "reviews") @Getter @Setter
public class ReviewEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "review_id") private Long reviewId;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "booking_id") private BookingEntity booking;
    @Column(name = "rating") private Integer rating;
    @Column(name = "comment") private String comment;
    @Column(name = "created_at") private OffsetDateTime createdAt;
}
