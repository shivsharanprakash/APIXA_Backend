package com.example.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Step 9 source security analysis input: one authorization rule per line so file/line evidence is
 * easy to inspect. Not a buildable class (no Spring Security on any classpath) — the analyzer reads
 * it as source text with Spoon in {@code noClasspath} mode plus the legacy requestMatchers scan.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/manager/**").hasAnyRole("ADMIN", "MANAGER")
                .requestMatchers("/api/users/**").hasAuthority("USER_READ")
                .requestMatchers("/reports/**").hasAnyAuthority("REPORT_READ", "REPORT_WRITE")
                .requestMatchers("/public/**").permitAll()
                .anyRequest().authenticated());
        return http.build();
    }
}
