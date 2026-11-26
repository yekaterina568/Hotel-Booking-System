package com.hotelbooking.admin.analytics;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;

public record ActivityFilter(Long hotelId, @DateTimeFormat(iso = DATE) LocalDate from,
                             @DateTimeFormat(iso = DATE) LocalDate to) {
    public void validate() {
        if (hotelId != null && hotelId < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid hotel.");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The start date must be on or before the end date.");
        }
        if (outsideSupportedYears(from) || outsideSupportedYears(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose dates with years between 0001 and 9999.");
        }
    }

    private static boolean outsideSupportedYears(LocalDate date) {
        return date != null && (date.getYear() < 1 || date.getYear() > 9999);
    }

    public boolean hasDates() {
        return from != null || to != null;
    }

    public MapSqlParameterSource parameters(ZoneId zone) {
        var parameters = new MapSqlParameterSource().addValue("hotelId", hotelId).addValue("zone", zone.getId());
        if (from != null) parameters.addValue("fromDate", from.atStartOfDay(zone).toOffsetDateTime());
        if (to != null) parameters.addValue("untilDate", to.plusDays(1).atStartOfDay(zone).toOffsetDateTime());
        return parameters;
    }

    public String hotelCondition(String column) {
        return hotelId == null ? "" : " AND " + column + " = :hotelId";
    }

    public String dateCondition(String column) {
        return (from == null ? "" : " AND " + column + " >= :fromDate")
                + (to == null ? "" : " AND " + column + " < :untilDate");
    }
}
