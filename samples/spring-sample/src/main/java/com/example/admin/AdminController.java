package com.example.admin;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Generic {@code @RequestMapping(value = ..., method = ...)} form.
 *
 * <p>The security annotation is present on purpose: Step 8 endpoint analysis must ignore it and only
 * report the endpoint. Security interpretation belongs to Step 9.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    @RequestMapping(value = "/users", method = RequestMethod.GET)
    public String users() {
        return "[]";
    }

    @RequestMapping(path = "/health", method = { RequestMethod.GET, RequestMethod.HEAD })
    public String health() {
        return "ok";
    }
}
