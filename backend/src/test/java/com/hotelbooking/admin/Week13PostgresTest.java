package com.hotelbooking.admin;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "WEEK13_TEST_JDBC_URL", matches = "jdbc:postgresql:.*")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Week13PostgresTest {
    private static final String ADMIN = "week13-admin:integration-only";
    private static final String CLERK = "week13-clerk:integration-only";
    private static final String FORMULA_HOTEL = "  =SUM(1,2) \"Гостиница\" <script>";
    private static final String SCHEMA = "week13_it_" + UUID.randomUUID().toString().replace("-", "");
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired CacheManager cache;

    @TestConfiguration(proxyBeanMethods = false)
    static class Users {
        @Bean
        UserDetailsService testUsers() {
            return new InMemoryUserDetailsManager(
                    User.withUsername("week13-admin").password("{noop}integration-only").roles("ADMIN").build(),
                    User.withUsername("week13-clerk").password("{noop}integration-only").roles("USER").build());
        }
    }

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry properties) throws Exception {
        String url = System.getenv("WEEK13_TEST_JDBC_URL");
        String user = System.getenv().getOrDefault("POSTGRES_USER", "postgres");
        String password = System.getenv().getOrDefault("POSTGRES_PASSWORD", "");
        new JdbcTemplate(new DriverManagerDataSource(url, user, password)).execute("CREATE SCHEMA " + SCHEMA);
        String isolatedUrl = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA;
        var dataSource = new DriverManagerDataSource(isolatedUrl, user, password);
        var baseline = new SpringLiquibase();
        baseline.setDataSource(dataSource);
        baseline.setDefaultSchema(SCHEMA);
        baseline.setChangeLog("classpath:db/changelog/week13-upgrade-baseline.yaml");
        baseline.afterPropertiesSet();

        var baselineDb = new JdbcTemplate(dataSource);
        baselineDb.update("INSERT INTO guests (passport_num, first_name, last_name, phone_num) VALUES ('BASE01','Old','Guest','000')");
        baselineDb.update("""
                INSERT INTO bookings (guest_id, room_id, state, expected_arrival)
                SELECT 1, room_id, 'ARCHIVED', '2026-01-05T00:00:00Z' FROM rooms WHERE room_number = 101
                """);
        baselineDb.update("""
                INSERT INTO bookings_archive (booking_id, guest_id, room_id, state, expected_arrival)
                SELECT booking_id, guest_id, room_id, state, expected_arrival FROM bookings
                """);
        properties.add("spring.datasource.url", () -> isolatedUrl);
        properties.add("spring.datasource.username", () -> user);
        properties.add("spring.datasource.password", () -> password);
        properties.add("spring.liquibase.default-schema", () -> SCHEMA);
        properties.add("hotel.reporting-zone", () -> "Asia/Almaty");
    }

    @BeforeAll
    void additiveMigrationKeepsPreviouslyAppliedChangesetsAndLegacyRows() {
        assertThat(db.queryForObject("SELECT count(*) FROM rooms WHERE hotel_id = 1", Long.class)).isEqualTo(4);
        assertThat(db.queryForObject("SELECT count(*) FROM bookings_archive WHERE state = 'ARCHIVED'", Long.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT count(*) FROM booking_status_history", Long.class)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM databasechangelog WHERE id LIKE 'week13-%'", Long.class)).isEqualTo(2);
        assertThat(db.queryForObject("SELECT count(*) FROM databasechangelog WHERE id NOT LIKE 'week13-%'", Long.class)).isEqualTo(6);
    }

    @BeforeEach
    void resetScratchSchema() {
        assertThat(db.queryForObject("SELECT current_schema()", String.class)).isEqualTo(SCHEMA);
        db.execute("""
                TRUNCATE hotels, rooms, guests, bookings, bookings_archive, refunds, reviews, keycards,
                         booking_status_history RESTART IDENTITY
                """);
        cache.getCacheNames().forEach(name -> cache.getCache(name).clear());
    }

    @Test
    void emptyDashboardAndReportsReturnZerosAndMissingRating() throws Exception {
        var data = get("/api/admin/dashboard?period=ALL_TIME");
        assertThat(data.at("/inventory/hotels").asInt()).isZero();
        assertThat(data.at("/activity/bookings").asInt()).isZero();
        assertThat(data.at("/activity/averagePublishedRating").isNull()).isTrue();
        assertThat(data.at("/bookingsOverTime").isEmpty()).isTrue();
        assertThat(data.at("/bookingStatuses/0/count").asInt()).isZero();
        var report = get("/api/admin/reports?type=BOOKINGS");
        assertThat(report.get("total").asInt()).isZero();
        assertThat(report.get("rows").isEmpty()).isTrue();
    }

    @Test
    void dashboardUsesScopedSqlAndInclusiveAlmatyDates() throws Exception {
        seed();
        var all = get("/api/admin/dashboard?period=ALL_TIME");
        assertThat(all.at("/inventory/rooms").asInt()).isEqualTo(3);
        assertThat(all.at("/inventory/guests").asInt()).isEqualTo(4);
        assertThat(all.at("/activity/bookings").asInt()).isEqualTo(5);
        assertThat(all.at("/activity/confirmed").asInt()).isEqualTo(2);
        assertThat(all.at("/activity/checkedIn").asInt()).isEqualTo(1);
        assertThat(all.at("/activity/cancelled").asInt()).isEqualTo(1);
        assertThat(all.at("/activity/archived").asInt()).isEqualTo(1);
        assertThat(all.at("/activity/refunds").asInt()).isEqualTo(3);
        assertThat(all.at("/activity/undatedRefunds").asInt()).isEqualTo(1);
        assertThat(all.at("/activity/averagePublishedRating").asDouble()).isEqualTo(3.67);
        assertThat(all.at("/bookingsPerHotel/0/count").asInt()).isEqualTo(4);
        assertThat(all.at("/bookingStatuses/4/label").asString()).isEqualTo("ARCHIVED");
        var filtered = get("/api/admin/dashboard?period=CUSTOM&hotelId=1&from=2026-02-10&to=2026-02-10");
        assertThat(filtered.at("/inventory/hotels").asInt()).isEqualTo(1);
        assertThat(filtered.at("/inventory/rooms").asInt()).isEqualTo(2);
        assertThat(filtered.at("/inventory/guests").asInt()).isEqualTo(2);
        assertThat(filtered.at("/activity/bookings").asInt()).isEqualTo(2);
        assertThat(filtered.at("/activity/refunds").asInt()).isEqualTo(1);
        assertThat(filtered.at("/activity/averagePublishedRating").asDouble()).isEqualTo(4);
        assertThat(filtered.at("/bookingsOverTime/0/date").asString()).isEqualTo("2026-02-10");
        assertThat(filtered.at("/bookingsOverTime/0/count").asInt()).isEqualTo(2);
        assertThat(filtered.at("/refundsOverTime/0/count").asInt()).isEqualTo(1);
        assertThat(filtered.at("/roomStatuses/0/count").asInt()).isEqualTo(1);
    }

    @Test
    void malformedDatesAndIncompatibleStatusReturn400() throws Exception {
        for (String path : new String[]{
                "/api/admin/dashboard?period=CUSTOM&from=2026-05-02&to=2026-05-01",
                "/api/admin/dashboard?period=CUSTOM&from=today&to=2026-05-01",
                "/api/admin/dashboard?period=CUSTOM&from=2026-05-02",
                "/api/admin/dashboard?hotelId=0",
                "/api/admin/reports?type=ROOMS&from=2026-02-10",
                "/api/admin/reports?type=REVIEWS&status=CONFIRMED"
        }) assertThat(request("GET", path, null, ADMIN).statusCode()).as(path).isEqualTo(400);
    }

    @Test
    void reportsApplyHotelDateAndStatusToPreviewCsvAndPrint() throws Exception {
        seed();
        String filters = "?type=BOOKINGS&hotelId=1&status=CONFIRMED&from=2026-02-10&to=2026-02-10";
        var report = get("/api/admin/reports" + filters);
        assertThat(report.get("title").asString()).isEqualTo("Booking report");
        assertThat(report.get("total").asInt()).isEqualTo(1);
        assertThat(report.at("/rows/0/0").asInt()).isEqualTo(1);
        assertThat(report.at("/rows/0/1").asString()).isEqualTo(FORMULA_HOTEL);
        assertThat(report.at("/rows/0/3").asString()).isEqualTo("CONFIRMED");

        var csv = request("GET", "/api/admin/reports/export" + filters, null, ADMIN);
        assertThat(csv.statusCode()).isEqualTo(200);
        assertThat(csv.headers().firstValue("Content-Disposition")).hasValue("attachment; filename=\"bookings-report.csv\"");
        assertThat(csv.body()).startsWith("\ufeff\"Booking ID\",\"Hotel\"")
                .contains("\r\n\"1\",\"'  =SUM(1,2) \"\"Гостиница\"\" <script>\",\"101\",\"CONFIRMED\"")
                .doesNotContain("TEST01", "000111", "\"2\",");
        assertThat(csv.body().lines()).hasSize(2);

        var print = request("GET", "/api/admin/reports/print" + filters, null, ADMIN);
        assertThat(print.statusCode()).isEqualTo(200);
        assertThat(print.body()).contains("Booking report", "Generated ", "Filters", "Summary", "CONFIRMED",
                "&lt;script&gt;", "2026-02-10").doesNotContain("<script>", "TEST01", "000111");

        assertThat(get("/api/admin/reports?type=ROOMS&hotelId=1&status=DIRTY").get("total").asInt()).isEqualTo(1);
        assertThat(get("/api/admin/reports?type=HOTELS&hotelId=1").at("/rows/0/4").asInt()).isEqualTo(4);
        assertThat(get("/api/admin/reports?type=REFUNDS&status=COMPLETED").at("/summary/1/value").asInt()).isEqualTo(25);
        assertThat(get("/api/admin/reports?type=REVIEWS&hotelId=1&status=PENDING").get("total").asInt()).isEqualTo(1);
    }

    @Test
    void reportAndBookingPagesReadOnlyTheirPageButCsvIncludesEveryMatchingRow() throws Exception {
        seed();
        db.update("""
                INSERT INTO bookings (guest_id, room_id, state, expected_arrival)
                SELECT 1, 1, 'CANCELLED', now() FROM generate_series(1, 60)
                """);
        var preview = get("/api/admin/reports?type=BOOKINGS&status=CANCELLED&page=2");
        assertThat(preview.get("total").asInt()).isEqualTo(61);
        assertThat(preview.get("rows").size()).isEqualTo(11);
        assertThat(request("GET", "/api/admin/reports/export?type=BOOKINGS&status=CANCELLED", null, ADMIN).body().lines())
                .hasSize(62);
        var page = get("/api/admin/bookings/search?hotelId=1&state=CANCELLED&search=101&page=2");
        assertThat(page.get("total").asInt()).isEqualTo(61);
        assertThat(page.get("rows").size()).isEqualTo(25);
        assertThat(page.at("/rows/0/state").asString()).isEqualTo("CANCELLED");
    }

    @Test
    void administrativeReadsDownloadsAndWritesRequireAdminRole() throws Exception {
        for (var endpoint : new String[][]{
                {"GET", "/api/admin/dashboard"}, {"GET", "/api/admin/reports?type=ROOMS"},
                {"GET", "/api/admin/reports/export?type=ROOMS"}, {"GET", "/api/admin/reports/print?type=ROOMS"},
                {"GET", "/api/admin/bookings/search"}, {"POST", "/api/admin/bookings/1/cancel"},
                {"POST", "/api/admin/bookings/1/archive"}, {"POST", "/api/admin/rooms"},
                {"PUT", "/api/admin/rooms/1"}, {"DELETE", "/api/admin/rooms/1"}
        }) {
            assertThat(request(endpoint[0], endpoint[1], null, null).statusCode()).as(endpoint[1]).isEqualTo(401);
            assertThat(request(endpoint[0], endpoint[1], null, CLERK).statusCode()).as(endpoint[1]).isEqualTo(403);
        }
        assertThat(request("GET", "/api/rooms", null, CLERK).statusCode()).isEqualTo(200);
        assertThat(request("POST", "/api/rooms", null, CLERK).statusCode()).isEqualTo(403);
        assertThat(request("DELETE", "/api/rooms/1", null, CLERK).statusCode()).isEqualTo(403);
        var preflight = HttpRequest.newBuilder(url("/api/admin/rooms"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).header("Origin", "https://example.org")
                .header("Access-Control-Request-Method", "POST").build();
        assertThat(HTTP.send(preflight, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
    }

    @Test
    void roomConstraintsAllowSameNumberAcrossHotelsAndRejectUnsafeMutations() throws Exception {
        seed();
        var create = request("POST", "/api/admin/rooms", room(2, 101, "DOUBLE", "DIRTY", "159.95"), ADMIN);
        assertThat(create.statusCode()).isEqualTo(201);
        long created = JSON.readTree(create.body()).get("roomId").asLong();
        assertThat(request("POST", "/api/admin/rooms", room(2, 101, "SINGLE", "DIRTY", "1"), ADMIN).statusCode())
                .isEqualTo(409);
        assertThat(request("POST", "/api/admin/rooms", room(2, 501, "SINGLE", "DIRTY", "123.456"), ADMIN).statusCode())
                .isEqualTo(400);
        String editPath = "/api/admin/rooms/" + created;
        var update = request("PUT", editPath, room(1, 502, "SUITE", "CLEANING", "205.25"), ADMIN);
        assertThat(update.statusCode()).isEqualTo(200);
        assertThat(db.queryForObject("SELECT hotel_id FROM rooms WHERE room_id = ?", Long.class, created)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT price_per_night FROM rooms WHERE room_id = ?", String.class, created)).isEqualTo("205.25");
        assertThat(db.queryForObject("SELECT room_type || '/' || cleaning_state FROM rooms WHERE room_id = ?", String.class, created))
                .isEqualTo("SUITE/CLEANING");
        assertThat(request("PUT", "/api/admin/rooms/1", room(2, 502, "SINGLE", "CLEANED", "50"), ADMIN).statusCode())
                .isEqualTo(409);
        assertThat(request("DELETE", "/api/admin/rooms/1", null, ADMIN).statusCode()).isEqualTo(409);
        assertThat(request("DELETE", editPath, null, ADMIN).statusCode()).isEqualTo(204);
        assertThat(db.queryForObject("SELECT count(*) FROM rooms WHERE room_id = ?", Long.class, created)).isZero();
    }

    @Test
    void concurrentRoomCreatesKeepOneRoomPerNumberAndHotel() throws Exception {
        seed();
        String body = room(2, 702, "DOUBLE", "DIRTY", "175");
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> request("POST", "/api/admin/rooms", body, ADMIN));
            var second = executor.submit(() -> request("POST", "/api/admin/rooms", body, ADMIN));
            assertThat(java.util.List.of(first.get().statusCode(), second.get().statusCode()))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(db.queryForObject("SELECT count(*) FROM rooms WHERE hotel_id = 2 AND room_number = 702", Long.class))
                .isEqualTo(1);
    }

    @Test
    void adminActionsReuseBookingRulesAndFrontDeskCheckoutStillWorks() throws Exception {
        seed();
        assertThat(request("POST", "/api/admin/bookings/1/archive", null, ADMIN).statusCode()).isEqualTo(409);
        assertThat(request("POST", "/api/admin/bookings/2/archive", null, ADMIN).statusCode()).isEqualTo(409);
        assertThat(request("POST", "/api/admin/bookings/2/cancel", null, ADMIN).statusCode()).isEqualTo(409);
        assertThat(request("POST", "/api/admin/bookings/1/cancel", null, ADMIN).statusCode()).isEqualTo(204);
        assertThat(request("POST", "/api/admin/bookings/1/archive", null, ADMIN).statusCode()).isEqualTo(204);
        assertThat(request("POST", "/api/admin/bookings/1/archive", null, ADMIN).statusCode()).isEqualTo(409);
        var detail = get("/api/admin/bookings/1");
        assertThat(detail.at("/booking/state").asString()).isEqualTo("ARCHIVED");
        assertThat(detail.get("archivedAt").isNull()).isFalse();
        assertThat(detail.at("/history/0/previousState").asString()).isEqualTo("CONFIRMED");
        assertThat(detail.at("/history/0/newState").asString()).isEqualTo("CANCELLED");
        assertThat(detail.at("/history/1/newState").asString()).isEqualTo("ARCHIVED");

        db.update("INSERT INTO keycards (booking_id, card_uid) VALUES (2,'W13INTEGRATION')");
        assertThat(request("POST", "/api/bookings/2/check-out", null, CLERK).statusCode()).isEqualTo(200);
        assertThat(db.queryForObject("SELECT state FROM bookings WHERE booking_id = 2", String.class)).isEqualTo("CHECKEDOUT");
        assertThat(db.queryForObject("SELECT cleaning_state FROM rooms WHERE room_id = 2", String.class)).isEqualTo("DIRTY");
        assertThat(db.queryForObject("SELECT is_active FROM keycards WHERE booking_id = 2", Boolean.class)).isFalse();
        assertThat(get("/api/admin/bookings/2").at("/history/0/newState").asString()).isEqualTo("CHECKEDOUT");
    }

    private JsonNode get(String path) throws Exception {
        var result = request("GET", path, null, ADMIN);
        assertThat(result.statusCode()).as(path + " " + result.body()).isEqualTo(200);
        return JSON.readTree(result.body());
    }

    private HttpResponse<String> request(String method, String path, String body, String auth) throws Exception {
        var request = HttpRequest.newBuilder(url(path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) request.header("Content-Type", "application/json");
        if (auth != null) request.header("Authorization",
                "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8)));
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private URI url(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String room(int hotel, int number, String type, String state, String price) {
        return """
                {"hotelId":%d,"roomNumber":%d,"roomType":"%s","cleaningState":"%s","pricePerNight":%s}
                """.formatted(hotel, number, type, state, price);
    }

    private void seed() {
        db.update("INSERT INTO hotels (name, city) VALUES (?, 'Almaty'), ('Other hotel', 'Astana')", FORMULA_HOTEL);
        db.update("""
                INSERT INTO rooms (hotel_id, room_number, room_type, cleaning_state, price_per_night) VALUES
                (1, 101, 'SINGLE', 'DIRTY', 120), (1, 102, 'SUITE', 'CLEANED', 240), (2, 301, 'TWIN', 'CLEANING', 100)
                """);
        db.update("""
                INSERT INTO guests (passport_num, first_name, last_name, phone_num) VALUES
                ('TEST01','Demo','One','000111'), ('TEST02','Demo','Two','000222'),
                ('TEST03','Demo','Three','000333'), ('TEST04','Demo','Four','000444')
                """);
        db.update("""
                INSERT INTO bookings (guest_id, room_id, state, created_at, expected_arrival, expected_departure) VALUES
                (1, 1, 'CONFIRMED', '2026-02-09T19:00:00Z', '2026-02-11T10:00:00Z', '2026-02-13T10:00:00Z'),
                (2, 2, 'CHECKEDIN', '2026-02-10T18:59:59Z', '2026-02-10T09:00:00Z', '2026-02-12T10:00:00Z'),
                (3, 3, 'CONFIRMED', '2026-02-10T12:00:00Z', '2026-02-11T10:00:00Z', '2026-02-12T10:00:00Z'),
                (1, 1, 'CANCELLED', '2026-02-10T19:00:00Z', '2026-02-10T10:00:00Z', null),
                (2, 2, 'ARCHIVED', '2026-02-09T18:59:59Z', '2026-02-10T10:00:00Z', null)
                """);
        db.update("""
                INSERT INTO refunds (booking_id, state, amount, processed_at) VALUES
                (1, 'COMPLETED', 25, '2026-02-10T09:00:00Z'), (5, 'PENDING', 15, null),
                (3, 'FAILED', 99, '2026-02-10T09:00:00Z')
                """);
        db.update("""
                INSERT INTO reviews (hotel_id, author_name, rating, comment, status, created_at) VALUES
                (1, 'Demo', 4, 'Review', 'PUBLISHED', '2026-02-10T09:00:00Z'),
                (1, 'Demo', 5, 'Review', 'PUBLISHED', '2026-02-11T09:00:00Z'),
                (1, 'Demo', 2, 'Review', 'PENDING', '2026-02-10T09:00:00Z'),
                (2, 'Demo', 2, 'Review', 'PUBLISHED', '2026-02-10T09:00:00Z')
                """);
    }
}
