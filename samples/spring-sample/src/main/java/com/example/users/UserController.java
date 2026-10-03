package com.example.users;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Class-level + method-level mapping composition, path variable and one query parameter. */
@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping
    public String listUsers(@RequestParam(name = "verbose", required = false) String verbose) {
        return "[]";
    }

    @GetMapping("/{id}")
    public String getUserById(@PathVariable String id) {
        return "{}";
    }

    @PostMapping
    public String createUser(@RequestBody String body) {
        return "{}";
    }

    @PutMapping("/{id}")
    public String updateUser(@PathVariable String id, @RequestBody String body) {
        return "{}";
    }

    @DeleteMapping("/{id}")
    public String deleteUser(@PathVariable String id) {
        return "";
    }
}
