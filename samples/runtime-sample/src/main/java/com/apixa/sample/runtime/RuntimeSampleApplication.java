package com.apixa.sample.runtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Step 13 runtime verification <b>target</b> — a deliberately small, deterministic Spring Boot API that
 * the APIXA Runtime Service (port 8087) probes.
 *
 * <p>This is a sample application, not part of the APIXA backend. It runs on its own port
 * ({@code apixa.runtime-sample.port}, default 9090) and exposes:
 *
 * <pre>
 *   GET /public/health  → 200 anonymous
 *   GET /admin/users    → 401 anonymous, 403 for an authenticated non-admin, 200 for an admin
 *   GET /user/profile   → 200 for any authenticated user
 *   anything else       → 404
 * </pre>
 *
 * <p>Authentication is HTTP Basic with <b>dummy, development-only in-memory credentials</b>. Nothing is
 * stored on disk and no password is ever logged.
 */
@SpringBootApplication
public class RuntimeSampleApplication {
    public static void main(String[] args) {
        SpringApplication.run(RuntimeSampleApplication.class, args);
    }
}