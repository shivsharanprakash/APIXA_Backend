package com.example.alias;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Multi-path class-level and method-level mappings: every declared combination must be produced. */
@RestController
@RequestMapping({ "/api/v1/alias", "/api/v2/alias" })
public class AliasController {

    @GetMapping({ "/items", "/things" })
    public String list() {
        return "[]";
    }
}
