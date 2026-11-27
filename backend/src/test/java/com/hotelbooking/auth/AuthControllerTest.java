package com.hotelbooking.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sign-in check endpoint. Login rules themselves are tested in AdminAccessTest with Spring Security. */
class AuthControllerTest {

    @Test
    @DisplayName("64. GET /api/auth/check answers 204 so the frontend knows the login worked")
    void check_returns204() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AuthController()).build();

        mockMvc.perform(get("/api/auth/check"))
                .andExpect(status().isNoContent());
    }
}
