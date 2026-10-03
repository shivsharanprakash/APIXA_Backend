package com.example.legacy;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** No class-level mapping: the method-level path is used exactly as declared. */
@RestController
public class LegacyUserController {

    /** Deliberate duplicate of UserController#listUsers — both source declarations must be kept. */
    @GetMapping("/api/users")
    public String listUsersLegacy() {
        return "[]";
    }

    /** Generic mapping without a method attribute: Spring maps it for all standard methods. */
    @RequestMapping(path = "/legacy/ping")
    public String ping() {
        return "pong";
    }
}
