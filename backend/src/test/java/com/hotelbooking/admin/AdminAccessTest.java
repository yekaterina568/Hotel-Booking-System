package com.hotelbooking.admin;

import com.hotelbooking.auth.AuthController;
import com.hotelbooking.booking.archive.service.BookingArchiveService;
import com.hotelbooking.booking.service.AvailabilityService;
import com.hotelbooking.booking.service.BookingService;
import com.hotelbooking.config.SecurityConfig;
import com.hotelbooking.guest.service.GuestService;
import com.hotelbooking.hotel.controller.HotelAdminController;
import com.hotelbooking.hotel.service.HotelService;
import com.hotelbooking.keycard.service.KeycardService;
import com.hotelbooking.refund.service.RefundService;
import com.hotelbooking.review.controller.ReviewAdminController;
import com.hotelbooking.review.service.ReviewService;
import com.hotelbooking.room.controller.RoomController;
import com.hotelbooking.room.service.RoomService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {HotelAdminController.class, ReviewAdminController.class, RoomController.class,
        AdminRecordsController.class, AuthController.class},
        properties = {"spring.security.user.name=admin-access-test", "spring.security.user.password=test-password"})
@Import(SecurityConfig.class)
@ImportAutoConfiguration({SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
class AdminAccessTest {
    private static final String AUTHORIZATION = "Basic " + Base64.getEncoder().encodeToString(
            "admin-access-test:test-password".getBytes(StandardCharsets.UTF_8));

    @TestConfiguration(proxyBeanMethods = false)
    static class CacheConfig {
        @Bean
        CacheManager cacheManager() {
            return new NoOpCacheManager();
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private HotelService hotels;

    @MockitoBean
    private ReviewService reviews;

    @MockitoBean
    private RoomService rooms;

    @MockitoBean
    private GuestService guests;

    @MockitoBean
    private BookingService bookings;

    @MockitoBean
    private AvailabilityService availability;

    @MockitoBean
    private BookingArchiveService archive;

    @MockitoBean
    private RefundService refunds;

    @MockitoBean
    private KeycardService keycards;

    @Test
    void unauthenticatedRequestsDoNotReachServices() throws Exception {
        mvc.perform(get("/api/admin/hotels"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, org.hamcrest.Matchers.startsWith("Basic")));
        mvc.perform(delete("/api/admin/reviews/1").header(HttpHeaders.AUTHORIZATION, "Basic invalid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/rooms"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/check"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(hotels, reviews, rooms, guests, bookings, archive, refunds, keycards, availability);
    }

    @Test
    void validCredentialsOpenAllApis() throws Exception {
        mvc.perform(get("/api/admin/hotels").header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.CACHE_CONTROL));
        mvc.perform(get("/api/rooms").header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/check").header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isNoContent());

        verify(hotels).findAll();
        verify(rooms).findAll();
    }

    @ParameterizedTest
    @ValueSource(strings = {"rooms", "guests", "bookings", "booking-archive", "refunds", "keycards"})
    void operationalTablesRequireAuthentication(String table) throws Exception {
        String path = "/api/admin/" + table;
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        verifyNoInteractions(rooms, guests, bookings, archive, refunds, keycards);

        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void browserCanSendAuthorizationHeaderAcrossTheFrontendOrigin() throws Exception {
        mvc.perform(options("/api/admin/reviews")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization, content-type"))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS));

        verifyNoInteractions(reviews);
    }

    @Test
    void fractionalRatingIsRejectedBeforeWritingAReview() throws Exception {
        mvc.perform(post("/api/admin/reviews")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hotelId":1,"authorName":"Guest","rating":4.5,"comment":"Example"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Rating must be a whole number from 1 to 5."));

        verifyNoInteractions(reviews);
    }
}
