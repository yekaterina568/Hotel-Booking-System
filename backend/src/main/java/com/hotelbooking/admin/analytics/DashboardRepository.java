package com.hotelbooking.admin.analytics;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static com.hotelbooking.admin.analytics.DashboardData.*;

@Repository
class DashboardRepository {
    private final NamedParameterJdbcTemplate jdbc;

    DashboardRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Inventory inventory(ActivityFilter filter, MapSqlParameterSource parameters) {
        String guests = filter.hotelId() == null ? "SELECT count(*) FROM guests" : """
                SELECT count(DISTINCT b.guest_id) FROM bookings b JOIN rooms r USING (room_id)
                WHERE r.hotel_id = :hotelId
                """;
        return jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM hotels WHERE TRUE %s),
                       (SELECT count(*) FROM rooms WHERE TRUE %s), (%s)
                """.formatted(filter.hotelCondition("hotel_id"), filter.hotelCondition("hotel_id"), guests),
                parameters, (rs, row) -> new Inventory(rs.getLong(1), rs.getLong(2), rs.getLong(3)));
    }

    List<Category> bookingStatuses(ActivityFilter filter, MapSqlParameterSource parameters) {
        return categories("""
                SELECT b.state, count(*) FROM bookings b JOIN rooms r USING (room_id)
                WHERE TRUE %s %s GROUP BY b.state
                """.formatted(filter.hotelCondition("r.hotel_id"), filter.dateCondition("b.created_at")), parameters);
    }

    List<Category> roomStatuses(ActivityFilter filter, MapSqlParameterSource parameters) {
        return categories("SELECT cleaning_state, count(*) FROM rooms WHERE TRUE "
                + filter.hotelCondition("hotel_id") + " GROUP BY cleaning_state", parameters);
    }

    List<HotelCount> bookingsPerHotel(ActivityFilter filter, MapSqlParameterSource parameters) {
        return jdbc.query("""
                SELECT h.hotel_id, h.name, count(b.booking_id)
                FROM hotels h LEFT JOIN rooms r USING (hotel_id)
                LEFT JOIN bookings b ON b.room_id = r.room_id %s
                WHERE TRUE %s GROUP BY h.hotel_id ORDER BY count(b.booking_id) DESC, h.name, h.hotel_id
                """.formatted(filter.dateCondition("b.created_at"), filter.hotelCondition("h.hotel_id")),
                parameters, (rs, row) -> new HotelCount(rs.getLong(1), rs.getString(2), rs.getLong(3)));
    }

    ReviewStats reviews(ActivityFilter filter, MapSqlParameterSource parameters) {
        return jdbc.query("""
                SELECT rating, count(*), round(avg(rating), 2)
                FROM reviews WHERE status = 'PUBLISHED' %s %s
                GROUP BY GROUPING SETS ((rating), ())
                ORDER BY rating NULLS LAST
                """.formatted(filter.hotelCondition("hotel_id"), filter.dateCondition("created_at")), parameters, rs -> {
            var counts = new java.util.ArrayList<Category>();
            BigDecimal average = null;
            while (rs.next()) {
                String rating = rs.getString(1);
                if (rating == null) average = rs.getBigDecimal(3);
                else counts.add(new Category(rating, rs.getLong(2)));
            }
            return new ReviewStats(counts, average);
        });
    }

    List<TrendPoint> trend(ActivityFilter filter, ZoneId zone, String unit, boolean refunds) {
        String timestamp = refunds ? "f.processed_at" : "b.created_at";
        String join = refunds ? "JOIN refunds f USING (booking_id)" : "";
        return jdbc.query("""
                SELECT cast(date_trunc(:unit, timezone(:zone, %s)) as date) AS period, count(*)
                FROM bookings b JOIN rooms r USING (room_id) %s
                WHERE TRUE %s %s
                GROUP BY period ORDER BY period NULLS LAST
                """.formatted(timestamp, join, filter.hotelCondition("r.hotel_id"), filter.dateCondition(timestamp)),
                filter.parameters(zone).addValue("unit", unit),
                (rs, row) -> new TrendPoint(rs.getObject(1, LocalDate.class), rs.getLong(2)));
    }

    private List<Category> categories(String sql, MapSqlParameterSource parameters) {
        return jdbc.query(sql, parameters, (rs, row) -> new Category(rs.getString(1), rs.getLong(2)));
    }

    record ReviewStats(List<Category> ratings, BigDecimal average) {}
}
