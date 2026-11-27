package com.hotelbooking.admin.report;

import com.hotelbooking.booking.model.BookingState;
import com.hotelbooking.refund.model.RefundState;
import com.hotelbooking.review.model.ReviewStatus;
import com.hotelbooking.room.model.CleaningState;

import java.util.Arrays;
import java.util.List;

public enum ReportType {
    BOOKINGS("Booking report", "Booking created", BookingState.values(), "created_at DESC, booking_id DESC",
            "Booking ID", "Hotel", "Room number", "Status", "Expected arrival", "Expected departure", "Created"),
    ROOMS("Room inventory report", null, CleaningState.values(), "hotel, hotel_id, room_number",
            "Room ID", "Hotel", "Room number", "Type", "Cleaning status", "Price per night"),
    HOTELS("Hotel performance report", "Bookings and reviews created", BookingState.values(), "hotel, hotel_id",
            "Hotel ID", "Hotel", "City", "Rooms", "Bookings", "Guests booked", "Published reviews", "Average rating"),
    REFUNDS("Refund report", "Refund processed", RefundState.values(), "processed_at DESC NULLS LAST, refund_id DESC",
            "Refund ID", "Booking ID", "Hotel", "Status", "Amount", "Processed"),
    REVIEWS("Guest review report", "Review created", ReviewStatus.values(), "created_at DESC, review_id DESC",
            "Review ID", "Hotel", "Rating", "Status", "Created");

    final String title;
    final String dateBasis;
    final List<String> statuses;
    final String order;
    final List<String> columns;

    ReportType(String title, String dateBasis, Enum<?>[] statuses, String order, String... columns) {
        this.title = title;
        this.dateBasis = dateBasis;
        this.statuses = Arrays.stream(statuses).map(Enum::name).toList();
        this.order = order;
        this.columns = List.of(columns);
    }
}
