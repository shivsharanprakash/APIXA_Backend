package com.apixa.sample.runtime;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Deterministic security configuration for the runtime sample target.
 *
 * <p>Rules, in Spring Security's first-match-wins order:
 * <ul>
 *   <li>{@code /public/**} → {@code permitAll} → 200 anonymous</li>
 *   <li>{@code /admin/**} → {@code hasRole("ADMIN")} → 401 anonymous, 403 for an authenticated
 *       non-admin, 200 for an admin</li>
 *   <li>any other authenticated request → 200 for a logged-in user, 401 otherwise</li>
 * </ul>
 *
 * <p>HTTP Basic is used so runtime verification can send a plain {@code Authorization} header. The two
 * users below are <b>dummy local fixtures</b>, defined in memory only — they are not credentials for any
 * real system and nothing is persisted or logged.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/public/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/user/**").authenticated()
                // Anything else is not a mapped endpoint at all, so it falls through to Spring's
                // 404 handler instead of being turned into a 401 by the security filter chain.
                .anyRequest().permitAll())
            .httpBasic(basic -> { });
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** admin / admin123 → ROLE_ADMIN, user / user123 → ROLE_USER (dummy local fixtures). */
    @Bean
    public InMemoryUserDetailsManager users(PasswordEncoder encoder) {
        UserDetails admin = User.withUsername("admin")
                .password(encoder.encode("admin123"))
                .roles("ADMIN")
                .build();
        UserDetails user = User.withUsername("user")
                .password(encoder.encode("user123"))
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(admin, user);
    }
}