package com.ecommerce.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Issues and validates HS256 JSON Web Tokens using the jjwt 0.13 API.
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final long tokenValidityMs;

    public JwtService(
            @Value("${jwt.secret.key}") String secret,
            @Value("${jwt.token.validity:1800000}") long tokenValidityMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.tokenValidityMs = tokenValidityMs > 0 ? tokenValidityMs : 1800000L;
    }

    public long getTokenValidityMs() {
        return tokenValidityMs;
    }

    /**
     * Builds a signed token whose subject is the user's email and which carries
     * the granted role names as a {@code roles} claim.
     */
    public String generateToken(String email, Set<String> roles) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(email)
                .claim("roles", roles)
                .issuedAt(new Date(now))
                .expiration(new Date(now + tokenValidityMs))
                .signWith(key)
                .compact();
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        Object roles = parseClaims(token).get("roles");
        return roles instanceof List<?> list ? (List<String>) list : List.of();
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
