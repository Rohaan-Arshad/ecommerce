package com.ecommerce.entity;

/**
 * Identifies how a user authenticates. Mirrors the CHECK constraint on
 * users.auth_provider in the ecommerce_db schema.
 */
public enum AuthProvider {
    LOCAL,
    GOOGLE,
    MICROSOFT
}
