package com.hotelbooking.review.controller;

import com.hotelbooking.admin.AdminApiErrors;
import com.hotelbooking.review.model.ReviewDto;
import com.hotelbooking.review.model.ReviewStatus;
import com.hotelbooking.review.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin panel: review moderation API (/api/admin/reviews). The service is mocked. */
class ReviewAdminControllerTest {

    private ReviewService reviewService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reviewService = mock(ReviewService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ReviewAdminController(reviewService))
                .setControllerAdvice(new AdminApiErrors())
                .build();
    }

    @Test
    @DisplayName("58. Admin: moderator publishes a review")
    void changeStatus_publishesReview() throws Exception {
        when(reviewService.changeStatus(5L, ReviewStatus.PUBLISHED)).thenReturn(
                new ReviewDto(5L, 1L, "Ann", (short) 4, "Nice room", ReviewStatus.PUBLISHED, null));

        mockMvc.perform(put("/api/admin/reviews/5/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "PUBLISHED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        verify(reviewService).changeStatus(5L, ReviewStatus.PUBLISHED);
    }

    @Test
    @DisplayName("59. Admin: a rating above 5 is rejected (400) and nothing is saved")
    void create_ratingTooHigh_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"hotelId": 1, "authorName": "Ann", "rating": 6, "comment": "Great"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Rating must be between 1 and 5."));

        verifyNoInteractions(reviewService);
    }

    @Test
    @DisplayName("60. Admin: deleting a review returns 204")
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/admin/reviews/8"))
                .andExpect(status().isNoContent());

        verify(reviewService).delete(8L);
    }
}
