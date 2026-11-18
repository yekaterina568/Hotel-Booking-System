package com.hotelbooking.admin;

import com.hotelbooking.hotel.controller.HotelAdminController;
import com.hotelbooking.hotel.service.HotelService;
import com.hotelbooking.review.controller.ReviewAdminController;
import com.hotelbooking.review.service.ReviewService;
import com.hotelbooking.room.controller.RoomController;
import com.hotelbooking.room.service.RoomService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {HotelAdminController.class, ReviewAdminController.class, RoomController.class},
        properties = "hotel.admin.token=admin-access-test")
class AdminAccessTest {
    private static final String TOKEN = "Bearer admin-access-test";

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

    @Test
    void unauthorizedRequestsDoNotReachAdminServices() throws Exception {
        mvc.perform(get("/api/admin/hotels"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
        mvc.perform(delete("/api/admin/reviews/1").header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(hotels, reviews);
    }

    @Test
    void validTokenOpensAdminApiAndRoomsRemainAccessibleWithoutIt() throws Exception {
        mvc.perform(get("/api/admin/hotels").header(HttpHeaders.AUTHORIZATION, TOKEN))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        mvc.perform(get("/api/rooms")).andExpect(status().isOk());

        verify(hotels).findAll();
        verify(rooms).findAll();
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
                        .header(HttpHeaders.AUTHORIZATION, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hotelId":1,"authorName":"Guest","rating":4.5,"comment":"Example"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Rating must be a whole number from 1 to 5."));

        verifyNoInteractions(reviews);
    }
}
