package com.hotelbooking.admin.report;

import com.hotelbooking.admin.analytics.ActivityFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Repository
class ReportRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate streamingJdbc;

    ReportRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
        var streaming = new JdbcTemplate(jdbc.getJdbcTemplate().getDataSource());
        streaming.setFetchSize(250);
        this.streamingJdbc = new NamedParameterJdbcTemplate(streaming);
    }

    String source(ReportType type, ActivityFilter filters, boolean statusSelected) {
        String hotel = filters.hotelCondition("h.hotel_id");
        String status = statusSelected ? " AND " : "";
        return switch (type) {
            case BOOKINGS -> """
                    SELECT b.booking_id, h.name AS hotel, r.room_number, b.state AS state,
                           b.expected_arrival, b.expected_departure, b.created_at
                    FROM bookings b JOIN rooms r USING (room_id) JOIN hotels h USING (hotel_id)
                    WHERE TRUE %s %s %s
                    """.formatted(hotel, filters.dateCondition("b.created_at"),
                    statusSelected ? status + "b.state = cast(:status as booking_state_enum)" : "");
            case ROOMS -> """
                    SELECT r.room_id, h.name AS hotel, r.room_number, r.room_type, r.cleaning_state,
                           r.price_per_night, h.hotel_id
                    FROM rooms r JOIN hotels h USING (hotel_id) WHERE TRUE %s %s
                    """.formatted(hotel, statusSelected ? status + "r.cleaning_state = cast(:status as cleaning_state_enum)" : "");
            case HOTELS -> """
                    WITH inventory AS (
                        SELECT hotel_id, count(*) AS rooms FROM rooms WHERE TRUE %s GROUP BY hotel_id
                    ), booking_activity AS (
                        SELECT r.hotel_id, count(*) AS bookings, count(DISTINCT b.guest_id) AS guests
                        FROM bookings b JOIN rooms r USING (room_id)
                        WHERE TRUE %s %s %s GROUP BY r.hotel_id
                    ), review_activity AS (
                        SELECT hotel_id, count(*) AS reviews, round(avg(rating), 2) AS rating
                        FROM reviews WHERE status = 'PUBLISHED' %s %s GROUP BY hotel_id
                    )
                    SELECT h.hotel_id, h.name AS hotel, h.city, coalesce(i.rooms, 0) AS rooms,
                           coalesce(b.bookings, 0) AS bookings, coalesce(b.guests, 0) AS guests,
                           coalesce(v.reviews, 0) AS reviews, v.rating
                    FROM hotels h LEFT JOIN inventory i USING (hotel_id)
                    LEFT JOIN booking_activity b USING (hotel_id) LEFT JOIN review_activity v USING (hotel_id)
                    WHERE TRUE %s
                    """.formatted(filters.hotelCondition("hotel_id"), filters.hotelCondition("r.hotel_id"),
                    filters.dateCondition("b.created_at"), statusSelected ? status + "b.state = cast(:status as booking_state_enum)" : "",
                    filters.hotelCondition("hotel_id"), filters.dateCondition("created_at"), hotel);
            case REFUNDS -> """
                    SELECT f.refund_id, f.booking_id, h.name AS hotel, f.state, f.amount, f.processed_at
                    FROM refunds f JOIN bookings b USING (booking_id) JOIN rooms r USING (room_id)
                    JOIN hotels h USING (hotel_id) WHERE TRUE %s %s %s
                    """.formatted(hotel, filters.dateCondition("f.processed_at"),
                    statusSelected ? status + "f.state = cast(:status as refund_state_enum)" : "");
            case REVIEWS -> """
                    SELECT v.review_id, h.name AS hotel, v.rating, v.status, v.created_at
                    FROM reviews v JOIN hotels h USING (hotel_id) WHERE TRUE %s %s %s
                    """.formatted(hotel, filters.dateCondition("v.created_at"), statusSelected ? status + "v.status = :status" : "");
        };
    }

    String hotelName(long hotelId) {
        return jdbc.query("SELECT name FROM hotels WHERE hotel_id = :id", java.util.Map.of("id", hotelId),
                rs -> rs.next() ? rs.getString(1) : null);
    }

    List<Object> summary(ReportType type, String source, SqlParameterSource parameters) {
        String aggregates = switch (type) {
            case BOOKINGS -> ", count(*) FILTER (WHERE state = 'CONFIRMED'), count(*) FILTER (WHERE state = 'ARCHIVED')";
            case ROOMS -> ", count(*) FILTER (WHERE cleaning_state = 'CLEANED')";
            case HOTELS -> ", coalesce(sum(rooms), 0), coalesce(sum(bookings), 0), coalesce(sum(reviews), 0)";
            case REFUNDS -> ", coalesce(sum(amount) FILTER (WHERE state = 'COMPLETED'), 0), count(*) FILTER (WHERE processed_at IS NULL)";
            case REVIEWS -> ", count(*) FILTER (WHERE status = 'PUBLISHED'), round(avg(rating), 2)";
        };
        return jdbc.queryForObject("SELECT count(*)" + aggregates + " FROM (" + source + ") report",
                parameters, (rs, row) -> values(rs, rs.getMetaData().getColumnCount()));
    }

    List<List<Object>> preview(ReportType type, String source, SqlParameterSource parameters) {
        return jdbc.query(ordered(type, source) + " LIMIT :limit OFFSET :offset", parameters,
                (rs, row) -> values(rs, type.columns.size()));
    }

    void export(ReportType type, String source, SqlParameterSource parameters, java.util.function.Consumer<List<Object>> writer) {
        streamingJdbc.query(ordered(type, source), parameters, rs -> {
            writer.accept(values(rs, type.columns.size()));
        });
    }

    private static String ordered(ReportType type, String source) {
        return source + " ORDER BY " + type.order;
    }

    private static List<Object> values(ResultSet rs, int columns) throws SQLException {
        var values = new ArrayList<>(columns);
        for (int index = 1; index <= columns; index++) {
            Object value = rs.getObject(index);
            if (value instanceof Timestamp timestamp) value = timestamp.toInstant().toString();
            if (value != null && !(value instanceof Number) && !(value instanceof String)) value = rs.getString(index);
            values.add(value);
        }
        return values;
    }
}
