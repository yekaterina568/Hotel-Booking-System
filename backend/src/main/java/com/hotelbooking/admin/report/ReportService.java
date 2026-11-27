package com.hotelbooking.admin.report;

import com.hotelbooking.admin.analytics.ActivityFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ReportService {
    public static final int PAGE_SIZE = 50;

    private final ReportRepository repository;
    private final ZoneId zone;

    ReportService(ReportRepository repository, @Value("${hotel.reporting-zone}") String zone) {
        this.repository = repository;
        this.zone = ZoneId.of(zone);
    }

    public record Field(String name, Object value) {}
    public record Preview(String title, OffsetDateTime generatedAt, List<Field> filters, List<Field> summary,
                          List<String> columns, List<List<Object>> rows, long total, int page, int pageSize) {}
    record Query(ReportType type, ActivityFilter filter, String status, String hotelName,
                 String source, MapSqlParameterSource parameters) {}

    Query prepare(ReportType type, ActivityFilter filters, String status) {
        filters.validate();
        if (filters.hasDates() && type.dateBasis == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room inventory does not use date filters.");
        }
        if (status != null && !type.statuses.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a status supported by this report.");
        }
        String hotelName = "All hotels";
        if (filters.hotelId() != null) {
            hotelName = repository.hotelName(filters.hotelId());
            if (hotelName == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Hotel not found.");
        }
        return new Query(type, filters, status, hotelName, repository.source(type, filters, status != null),
                filters.parameters(zone).addValue("status", status));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Preview preview(ReportType type, ActivityFilter filters, String status, int page) {
        if (page < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a positive page number.");
        Query query = prepare(type, filters, status);
        List<Field> summary = summary(query);
        var parameters = query.parameters().addValue("limit", PAGE_SIZE).addValue("offset", (page - 1L) * PAGE_SIZE);
        return new Preview(type.title, OffsetDateTime.now(zone), filters(query), summary, type.columns,
                repository.preview(type, query.source(), parameters), ((Number) summary.getFirst().value()).longValue(),
                page, PAGE_SIZE);
    }

    @Transactional(readOnly = true)
    public void writeCsv(Query query, OutputStream output) throws IOException {
        var writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
        writer.write('\ufeff');
        csvRow(writer, query.type().columns);
        exportRows(query, row -> csvRow(writer, row));
        writer.flush();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public void writePrint(Query query, OutputStream output) throws IOException {
        var writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
        writer.write("""
                <!doctype html><html lang="en"><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s</title>
                <style>
                body{font:14px system-ui,sans-serif;margin:2rem;color:#17233a}
                table{border-collapse:collapse;width:100%%}th,td{padding:.5rem;text-align:left;border:1px solid #bac8d8}
                th{background:#e8eff7}tr{break-inside:avoid}
                @media print{body{margin:0}.print-help{display:none}thead{display:table-header-group}}
                </style>
                <main><h1>%s</h1><p>Generated %s</p>
                <p class="print-help">Use your browser’s Print command to print or save this report as a PDF.</p>
                """.formatted(html(query.type().title), html(query.type().title), html(OffsetDateTime.now(zone))));
        writeValues(writer, "Filters", filters(query));
        var summary = summary(query);
        writeValues(writer, "Summary", summary);
        writer.write("<table><thead>");
        htmlRow(writer, query.type().columns, true);
        writer.write("</thead><tbody>");
        if (((Number) summary.getFirst().value()).longValue() == 0) {
            writer.write("<tr><td colspan=\"" + query.type().columns.size() + "\">No records match the selected filters.</td></tr>");
        }
        exportRows(query, row -> htmlRow(writer, row, false));
        writer.write("</tbody></table></main></html>");
        writer.flush();
    }

    private List<Field> filters(Query query) {
        var filter = query.filter();
        var values = new ArrayList<Field>();
        values.add(new Field("Hotel", query.hotelName()));
        values.add(new Field(query.type() == ReportType.HOTELS ? "Booking status" : "Status",
                query.status() == null ? "All statuses" : query.status()));
        if (query.type().dateBasis != null) {
            values.add(new Field("Date basis", query.type().dateBasis + " (" + zone.getId() + ")"));
            values.add(new Field("From", filter.from() == null ? "Any date" : filter.from()));
            values.add(new Field("Through", filter.to() == null ? "Any date" : filter.to()));
        }
        return values;
    }

    private List<Field> summary(Query query) {
        List<String> labels = switch (query.type()) {
            case BOOKINGS -> List.of("Bookings", "Confirmed", "Archived");
            case ROOMS -> List.of("Rooms", "Cleaned");
            case HOTELS -> List.of("Hotels", "Rooms", "Bookings", "Published reviews");
            case REFUNDS -> List.of("Refunds", "Completed refund amount", "Without processed date");
            case REVIEWS -> List.of("Reviews", "Published", "Average rating of matching reviews");
        };
        var totals = repository.summary(query.type(), query.source(), query.parameters());
        var summary = new ArrayList<Field>();
        for (int index = 0; index < labels.size(); index++) summary.add(new Field(labels.get(index), totals.get(index)));
        return summary;
    }

    private void exportRows(Query query, RowWriter writer) throws IOException {
        try {
            repository.export(query.type(), query.source(), query.parameters(), row -> {
                try {
                    writer.write(row);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
    }

    @FunctionalInterface
    private interface RowWriter {
        void write(List<?> row) throws IOException;
    }

    private static void csvRow(Writer writer, List<?> values) throws IOException {
        writer.write(values.stream().map(ReportService::csvCell).collect(Collectors.joining(",")));
        writer.write("\r\n");
    }

    private static String csvCell(Object value) {
        String text = value == null ? "" : value.toString();
        if (value instanceof String && !text.isEmpty()) {
            String trimmed = text.replaceFirst("^[\\p{Z}\\p{C}\\s]+", "");
            if (Stream.of("=", "+", "-", "@", "＝", "＋", "－", "＠").anyMatch(trimmed::startsWith)
                    || text.startsWith("\t") || text.startsWith("\r") || text.startsWith("\n")) {
                text = "'" + text;
            }
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static void htmlRow(Writer writer, List<?> values, boolean heading) throws IOException {
        String open = heading ? "<th scope=\"col\">" : "<td>";
        String close = heading ? "</th>" : "</td>";
        writer.write("<tr>");
        for (Object value : values) writer.write(open + html(value) + close);
        writer.write("</tr>");
    }

    private static void writeValues(Writer writer, String heading, List<Field> values) throws IOException {
        writer.write("<h2>" + html(heading) + "</h2><dl>");
        for (Field value : values) {
            writer.write("<dt>" + html(value.name()) + "</dt><dd>" + html(value.value()) + "</dd>");
        }
        writer.write("</dl>");
    }

    private static String html(Object value) {
        return value == null ? "—" : HtmlUtils.htmlEscape(value.toString());
    }
}
