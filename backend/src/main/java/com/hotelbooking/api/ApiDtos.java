package com.hotelbooking.api;

import jakarta.validation.constraints.*;
import com.hotelbooking.room.model.*;
import java.math.BigDecimal;
import java.time.*;

/** API boundaries: no entity input binding and no passport/phone/key UID in responses. */
public final class ApiDtos {
    private ApiDtos() {}
    public record GuestRequest(@NotBlank @Pattern(regexp="[A-Z0-9]{6,20}") String passportNum,
        @NotBlank @Size(max=30) String firstName, @NotBlank @Size(max=30) String lastName,
        @NotBlank @Pattern(regexp="\\+?[0-9]{7,14}") String phoneNum) {}
    public record GuestResponse(Long guestId, String firstName, String lastName) {}
    public record HotelRequest(@NotBlank @Size(max=100) String name, @NotBlank @Size(max=100) String city,
        @NotBlank @Size(max=60) String timezone) {}
    public record HotelResponse(Long hotelId, String name, String city, String timezone) {}
    public record RoomRequest(@NotNull @Positive Long hotelId, @NotNull @Positive Integer roomNumber,
        @NotNull RoomType roomType, @NotNull CleaningState cleaningState,
        @NotNull @DecimalMin("0.00") @DecimalMax("99999999.99") @Digits(integer=8,fraction=2) BigDecimal pricePerNight) {}
    public record RoomUpdate(@NotNull CleaningState cleaningState,
        @DecimalMin("0.00") @DecimalMax("99999999.99") @Digits(integer=8,fraction=2) BigDecimal pricePerNight) {}
    public record RoomResponse(Long roomId, Long hotelId, String hotelName, Integer roomNumber,
        RoomType roomType, CleaningState cleaningState, BigDecimal pricePerNight) {}
    public record BookingRequest(@NotNull @Positive Long guestId, @NotNull @Positive Long roomId,
        @NotNull LocalDate checkInDate, @NotNull LocalDate checkOutDate, @NotNull OffsetDateTime expectedArrival) {}
    public record BookingResponse(Long bookingId, Long guestId, String guestName, Long roomId, Integer roomNumber,
        Long hotelId, String state, OffsetDateTime expectedArrival, LocalDate checkInDate,
        LocalDate checkOutDate, CleaningState cleaningState, OffsetDateTime checkedInAt) {}
    public record AvailabilityResponse(Long roomId, LocalDate checkInDate, LocalDate checkOutDate, boolean available) {}
    public record CostResponse(Long bookingId, int nights, BigDecimal nightlyRate, BigDecimal subtotal,
        BigDecimal serviceFee, BigDecimal total, String currency, boolean estimatedLegacy) {}
    public record PaymentRequest(@NotNull @Positive Long bookingId,
        @NotBlank @Size(max=80) String idempotencyKey, boolean simulateDecline) {}
    public record PaymentResponse(Long paymentId, Long bookingId, BigDecimal amount, String currency,
        String state, String providerReference, OffsetDateTime createdAt) {}
    public record RefundRequest(@NotNull @Positive Long bookingId,
        @NotNull @DecimalMin("0.01") @Digits(integer=12,fraction=2) BigDecimal amount,
        @NotBlank @Size(max=1000) String refundReason, @NotBlank @Size(max=80) String idempotencyKey) {}
    public record RefundResponse(Long refundId, Long bookingId, Long paymentId, BigDecimal amount,
        String refundReason, String state, OffsetDateTime processedAt) {}
    public record KeycardRequest(@NotNull @Positive Long bookingId) {}
    public record KeycardResponse(Long keycardId, Long bookingId, boolean active, OffsetDateTime issueTimestamp) {}
    public record ReviewRequest(@NotNull @Positive Long bookingId, @NotNull @Min(1) @Max(5) Integer rating,
        @NotBlank @Size(max=1000) String comment) {}
    public record ReviewResponse(Long reviewId, Long bookingId, Long hotelId, Integer rating, String comment) {}
    public record ArchiveResponse(Long bookingId, Long guestId, Long roomId, String previousState,
        LocalDate checkInDate, LocalDate checkOutDate, OffsetDateTime archivedAt) {}
}
