package com.hotelbooking.admin;

import com.hotelbooking.booking.model.BookingState;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

import static com.hotelbooking.admin.AdminBookingService.*;

@Repository
class AdminBookingRepository {
    private static final String SOURCE = """
            FROM bookings b JOIN rooms r USING (room_id) JOIN hotels h USING (hotel_id)
            """;
    private static final String FIELDS = """
            SELECT b.booking_id, b.guest_id, r.room_id, r.room_number, h.hotel_id, h.name, b.state,
                   b.created_at, b.expected_arrival, b.expected_departure, b.checked_out_at
            """;

    private final NamedParameterJdbcTemplate jdbc;

    AdminBookingRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    BookingPage search(Long hotelId, BookingState state, String search, int page, int size) {
        String conditions = " WHERE TRUE"
                + (hotelId == null ? "" : " AND h.hotel_id = :hotelId")
                + (state == null ? "" : " AND b.state = cast(:state as booking_state_enum)")
                + (search.isEmpty() ? "" : """
                    AND strpos(lower(concat_ws(' ', b.booking_id, b.guest_id, r.room_number, h.name)), lower(:search)) > 0
                    """);
        var parameters = new MapSqlParameterSource().addValue("hotelId", hotelId)
                .addValue("state", state == null ? null : state.name()).addValue("search", search)
                .addValue("size", size).addValue("offset", (page - 1L) * size);
        long total = jdbc.queryForObject("SELECT count(*) " + SOURCE + conditions, parameters, Long.class);
        List<BookingRow> rows = jdbc.query(FIELDS + SOURCE + conditions + """
                 ORDER BY b.booking_id DESC LIMIT :size OFFSET :offset
                """, parameters, (rs, row) -> booking(rs));
        return new BookingPage(rows, total, page, size);
    }

    BookingDetail detail(long id) {
        var parameters = new MapSqlParameterSource("id", id);
        return jdbc.query(FIELDS.stripTrailing() + ", a.archived_at " + SOURCE + """
                LEFT JOIN bookings_archive a USING (booking_id) WHERE b.booking_id = :id
                """, parameters, rs -> {
            if (!rs.next()) return null;
            return new BookingDetail(booking(rs), rs.getObject("archived_at", OffsetDateTime.class), history(parameters));
        });
    }

    private List<StatusChange> history(MapSqlParameterSource parameters) {
        return jdbc.query("""
                SELECT previous_state, new_state, changed_at FROM booking_status_history
                WHERE booking_id = :id ORDER BY event_id
                """, parameters, (rs, row) -> new StatusChange(BookingState.valueOf(rs.getString(1)),
                BookingState.valueOf(rs.getString(2)), rs.getObject(3, OffsetDateTime.class)));
    }

    private static BookingRow booking(ResultSet rs) throws SQLException {
        return new BookingRow(rs.getLong("booking_id"), rs.getLong("guest_id"), rs.getLong("room_id"),
                rs.getInt("room_number"), rs.getLong("hotel_id"), rs.getString("name"),
                BookingState.valueOf(rs.getString("state")), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("expected_arrival", OffsetDateTime.class), rs.getObject("expected_departure", OffsetDateTime.class),
                rs.getObject("checked_out_at", OffsetDateTime.class));
    }
}
