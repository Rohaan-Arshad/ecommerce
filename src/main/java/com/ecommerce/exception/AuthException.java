package com.ecommerce.exception;

/**
 * Thrown when registration or login fails for a business reason
 * (duplicate email, bad credentials, blocked account, ...).
 */
public class AuthException extends RuntimeException {

    public AuthException(String message) {
        super(message);
    }
}
