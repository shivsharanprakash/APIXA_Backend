package com.example.secured;

import jakarta.annotation.security.RolesAllowed;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

/**
 * Step 9 method-level security input. None of these methods is an endpoint (no mapping
 * annotation), so the Step 8 endpoint count is unchanged: Step 9 records the declaring
 * class/method plus annotation line evidence and never maps a rule to an HTTP path.
 */
@Component
public class SecuredMethods {

    @PreAuthorize("hasRole('ADMIN')")
    public String byRole() {
        return "ok";
    }

    @PreAuthorize("hasAuthority('USER_READ')")
    public String byAuthority() {
        return "ok";
    }

    @Secured("ROLE_ADMIN")
    public String secured() {
        return "ok";
    }

    @RolesAllowed("ADMIN")
    public String rolesAllowed() {
        return "ok";
    }

    @PreAuthorize("hasRole('ADMIN') and #id == authentication.name")
    public String complex(String id) {
        return "ok";
    }

    @PreAuthorize("hasRole(ADMIN_ROLE)")
    public String dynamic() {
        return "ok";
    }

    @PreAuthorize("permitAll()")
    public String open() {
        return "ok";
    }
}
