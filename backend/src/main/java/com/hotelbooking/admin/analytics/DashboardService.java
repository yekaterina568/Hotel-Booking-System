package com.hotelbooking.admin.analytics;

import com.hotelbooking.booking.model.BookingState;
import com.hotelbooking.room.model.CleaningState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hotelbooking.admin.analytics.DashboardData.*;

@Service
public class DashboardService {
    public enum Period { LAST_7_DAYS, LAST_30_DAYS, ALL_TIME, CUSTOM }

    private final DashboardRepository repository;
    private final ZoneId zone;

    DashboardService(DashboardRepository repository, @Value("${hotel.reporting-zone}") String zone) {
        this.repository = repository;
        this.zone = ZoneId.of(zone);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DashboardData get(Period period, ActivityFilter requested) {
        requested.validate();
        LocalDate today = LocalDate.now(zone);
        ActivityFilter filter = switch (period) {
            case LAST_7_DAYS -> new ActivityFilter(requested.hotelId(), today.minusDays(6), today);
            case LAST_30_DAYS -> new ActivityFilter(requested.hotelId(), today.minusDays(29), today);
            case ALL_TIME -> new ActivityFilter(requested.hotelId(), null, null);
            case CUSTOM -> {
                if (requested.from() == null || requested.to() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter both dates for a custom period.");
                }
                yield requested;
            }
        };
        String unit = granularity(filter);
        var parameters = filter.parameters(zone);
        var bookingStatuses = counts(repository.bookingStatuses(filter, parameters));
        var reviews = repository.reviews(filter, parameters);
        var refunds = repository.trend(filter, zone, unit, true);
        long refundCount = refunds.stream().mapToLong(TrendPoint::count).sum();
        long undatedRefunds = refunds.stream().filter(point -> point.date() == null).mapToLong(TrendPoint::count).sum();
        var activity = new Activity(bookingStatuses.values().stream().mapToLong(Long::longValue).sum(),
                count(bookingStatuses, BookingState.CONFIRMED), count(bookingStatuses, BookingState.CHECKEDIN),
                count(bookingStatuses, BookingState.CHECKEDOUT), count(bookingStatuses, BookingState.CANCELLED),
                count(bookingStatuses, BookingState.ARCHIVED), refundCount, undatedRefunds, reviews.average());

        return new DashboardData(filter, zone.getId(), unit, repository.inventory(filter, parameters), activity,
                fillTrend(repository.trend(filter, zone, unit, false), filter, unit),
                categories(enumNames(BookingState.values()), bookingStatuses),
                categories(enumNames(CleaningState.values()), counts(repository.roomStatuses(filter, parameters))),
                repository.bookingsPerHotel(filter, parameters), categories(List.of("1", "2", "3", "4", "5"), counts(reviews.ratings())),
                fillTrend(refunds.stream().filter(point -> point.date() != null).toList(), filter, unit));
    }

    private static String granularity(ActivityFilter filter) {
        if (!filter.hasDates()) return "month";
        long days = ChronoUnit.DAYS.between(filter.from(), filter.to());
        return days < 91 ? "day" : days < 730 ? "month" : "year";
    }

    private static List<TrendPoint> fillTrend(List<TrendPoint> rows, ActivityFilter filter, String unit) {
        if (rows.isEmpty()) return rows;
        LocalDate from = filter.hasDates() ? truncate(filter.from(), unit) : rows.getFirst().date();
        LocalDate to = filter.hasDates() ? truncate(filter.to(), unit) : rows.getLast().date();
        // Keep an all-time or exceptionally long period readable and bound the number of buckets.
        if (ChronoUnit.MONTHS.between(from, to) > 1_200 && !unit.equals("day")) return rows;
        var counts = rows.stream().collect(Collectors.toMap(TrendPoint::date, TrendPoint::count));
        var points = new ArrayList<TrendPoint>();
        for (LocalDate date = from; !date.isAfter(to); date = switch (unit) {
            case "day" -> date.plusDays(1);
            case "year" -> date.plusYears(1);
            default -> date.plusMonths(1);
        }) {
            points.add(new TrendPoint(date, counts.getOrDefault(date, 0L)));
        }
        return points;
    }

    private static LocalDate truncate(LocalDate date, String unit) {
        return switch (unit) {
            case "day" -> date;
            case "year" -> date.withDayOfYear(1);
            default -> date.withDayOfMonth(1);
        };
    }

    private static Map<String, Long> counts(List<Category> groups) {
        return groups.stream().collect(Collectors.toMap(Category::label, Category::count));
    }

    private static List<String> enumNames(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    private static List<Category> categories(List<String> labels, Map<String, Long> counts) {
        return labels.stream().map(label -> new Category(label, counts.getOrDefault(label, 0L))).toList();
    }

    private static long count(Map<String, Long> groups, BookingState state) {
        return groups.getOrDefault(state.name(), 0L);
    }
}
