package com.ecommerce.security;

import com.ecommerce.entity.Role;
import com.ecommerce.entity.User;
import org.springframework.stereotype.Component;

/**
 * Decides where a user lands after login based on their role:
 * admins go to the admin console, everyone else to the customer dashboard.
 */
@Component
public class LoginRedirectResolver {

    public static final String ADMIN_HOME = "/admin/dashboard";
    public static final String USER_HOME = "/dashboard";

    public String homeFor(User user) {
        boolean admin = user.getRoles().stream()
                .map(Role::getName)
                .anyMatch("ADMIN"::equals);
        return admin ? ADMIN_HOME : USER_HOME;
    }
}
