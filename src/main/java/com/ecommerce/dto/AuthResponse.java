package com.ecommerce.dto;

import java.util.Set;

/**
 * Response returned to API clients after a successful authentication.
 */
public record AuthResponse(
        String token,
        String tokenType,
        long expiresInMs,
        String email,
        String fullName,
        String authProvider,
        Set<String> roles
) {
}
