package com.hotelbooking.admin.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DashboardData(
        ActivityFilter filters, String zone, String granularity, Inventory inventory, Activity activity,
        List<TrendPoint> bookingsOverTime, List<Category> bookingStatuses, List<Category> roomStatuses,
        List<HotelCount> bookingsPerHotel, List<Category> reviewRatings, List<TrendPoint> refundsOverTime
) {
    public record Inventory(long hotels, long rooms, long guests) {}
    public record Activity(long bookings, long confirmed, long checkedIn, long checkedOut, long cancelled,
                           long archived, long refunds, long undatedRefunds, BigDecimal averagePublishedRating) {}
    public record TrendPoint(LocalDate date, long count) {}
    public record Category(String label, long count) {}
    public record HotelCount(long hotelId, String name, long count) {}
}
