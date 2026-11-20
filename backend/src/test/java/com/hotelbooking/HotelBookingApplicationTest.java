package com.hotelbooking;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.security.user.name=test-user",
        "spring.security.user.password=test-password"
})
class HotelBookingApplicationTest {

    @Test
    void contextLoads() {
    }

}
