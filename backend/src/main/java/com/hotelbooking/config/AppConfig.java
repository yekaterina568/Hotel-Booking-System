package com.hotelbooking.config;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.config.http.SessionCreationPolicy;
import java.time.Duration;
@Configuration @EnableCaching
public class AppConfig {
    @Bean CacheManager cacheManager() {
        var manager = new CaffeineCacheManager("rooms");
        manager.setCaffeine(Caffeine.newBuilder().maximumSize(100).expireAfterWrite(Duration.ofMinutes(5)).recordStats());
        return new TransactionAwareCacheManagerProxy(manager);
    }
    @Bean UserDetailsService staff(@Value("${hotel.staff.username}") String name, @Value("${hotel.staff.password}") String password) {
        if (password.isBlank()) throw new IllegalArgumentException("HOTEL_STAFF_PASSWORD must be configured");
        return new InMemoryUserDetailsManager(User.withUsername(name).password("{bcrypt}" + new BCryptPasswordEncoder().encode(password)).roles("STAFF").build());
    }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        // Stateless Basic header API; no cookie/session authentication. JSON mutations
        // and disabled CORS prevent credentialed cross-origin browser form requests.
        return http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(c -> c.disable())
            .authorizeHttpRequests(a -> a.requestMatchers("/", "/index.html", "/app.js", "/styles.css", "/favicon.ico", "/actuator/health", "/error").permitAll().anyRequest().hasRole("STAFF"))
            .httpBasic(b -> b.authenticationEntryPoint((req,res,e) -> res.sendError(401,"Staff authentication required")))
            .build();
    }
}
