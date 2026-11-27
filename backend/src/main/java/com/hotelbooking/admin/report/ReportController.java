package com.hotelbooking.admin.report;

import com.hotelbooking.admin.analytics.ActivityFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.Locale;

@RestController
@RequestMapping("/api/admin/reports")
public class ReportController {
    private final ReportService reports;

    ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping
    public ReportService.Preview preview(@RequestParam ReportType type, @ModelAttribute ActivityFilter filters,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(defaultValue = "1") int page) {
        return reports.preview(type, filters, status, page);
    }

    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(@RequestParam ReportType type, @ModelAttribute ActivityFilter filters,
                                                         @RequestParam(required = false) String status) {
        var query = reports.prepare(type, filters, status);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\""
                        + type.name().toLowerCase(Locale.ROOT) + "-report.csv\"")
                .body(output -> reports.writeCsv(query, output));
    }

    @GetMapping("/print")
    public ResponseEntity<StreamingResponseBody> print(@RequestParam ReportType type, @ModelAttribute ActivityFilter filters,
                                                        @RequestParam(required = false) String status) {
        var query = reports.prepare(type, filters, status);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
                .body(output -> reports.writePrint(query, output));
    }
}
