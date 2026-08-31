package com.ecommerce.dto;

import com.ecommerce.entity.Role;
import com.ecommerce.entity.User;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The "main things" about the logged-in user that we keep in the HTTP session.
 * Implements {@link Serializable} so the servlet container can store it in the
 * session. This is what makes the app remember who is logged in server-side.
 */
public record SessionUser(
        Long id,
        String fullName,
        String email,
        String authProvider,
        Set<String> roles,
        LocalDateTime loginAt
) implements Serializable {

    public static SessionUser from(User user) {
        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
        return new SessionUser(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getAuthProvider().name(),
                roleNames,
                LocalDateTime.now()
        );
    }

    public boolean isAdmin() {
        return roles != null && roles.contains("ADMIN");
    }
}
