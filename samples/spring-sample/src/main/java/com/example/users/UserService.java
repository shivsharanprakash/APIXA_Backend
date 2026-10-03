package com.example.users;

import org.springframework.stereotype.Service;

/**
 * Not a controller: scanned by the analyzer but must never contribute an endpoint, even though it
 * sits next to {@link UserController} in the same package.
 */
@Service
public class UserService {

    public String findById(String id) {
        return id;
    }
}
