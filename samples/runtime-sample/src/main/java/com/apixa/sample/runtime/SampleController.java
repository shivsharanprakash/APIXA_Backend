package com.apixa.sample.runtime;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** The three endpoints the Step 13 runtime verification probes. */
@RestController
public class SampleController {

    /** Public: always 200, no authentication needed. */
    @GetMapping("/public/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "apixa-runtime-sample");
    }

    /** Admin area: 401 anonymous, 403 authenticated non-admin, 200 admin. */
    @GetMapping("/admin/users")
    public Map<String, Object> adminUsers(Authentication auth) {
        return Map.of("endpoint", "/admin/users", "authenticatedAs", auth.getName());
    }

    /** Any authenticated user: 200. */
    @GetMapping("/user/profile")
    public Map<String, Object> profile(Authentication auth) {
        return Map.of("endpoint", "/user/profile", "authenticatedAs", auth.getName());
    }
}