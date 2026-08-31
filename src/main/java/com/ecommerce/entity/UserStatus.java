package com.ecommerce.entity;

/**
 * Account status. Mirrors the CHECK constraint on users.status.
 */
public enum UserStatus {
    ACTIVE,
    INACTIVE,
    BLOCKED
}
