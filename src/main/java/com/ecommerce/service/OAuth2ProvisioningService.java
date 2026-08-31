package com.ecommerce.service;

import com.ecommerce.entity.AuthProvider;
import com.ecommerce.entity.Role;
import com.ecommerce.entity.User;
import com.ecommerce.entity.UserStatus;
import com.ecommerce.exception.AuthException;
import com.ecommerce.repository.RoleRepository;
import com.ecommerce.repository.UserRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Creates or updates the local {@link User} record that backs a Google or
 * Microsoft OAuth2 login, so every authenticated principal exists in our DB.
 */
@Service
public class OAuth2ProvisioningService {

    private static final String DEFAULT_ROLE = "CUSTOMER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public OAuth2ProvisioningService(UserRepository userRepository, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Transactional
    public User upsert(String registrationId, OAuth2User oAuth2User) {
        AuthProvider provider = mapProvider(registrationId);
        Map<String, Object> attrs = oAuth2User.getAttributes();

        String providerUserId = stringAttr(attrs, "sub", "id", "oid");
        String email = firstNonBlank(
                stringAttr(attrs, "email", "mail", "preferred_username", "upn"));
        if (email == null) {
            throw new AuthException("The " + provider + " account did not provide an email address.");
        }
        email = email.toLowerCase();

        String firstName = firstNonBlank(stringAttr(attrs, "given_name", "givenName"));
        String lastName = firstNonBlank(stringAttr(attrs, "family_name", "surname"));
        String displayName = firstNonBlank(stringAttr(attrs, "name", "displayName"));
        if (firstName == null) {
            firstName = displayName != null ? displayName : email;
        }

        // Prefer an existing account for this email so a user is not duplicated
        // when switching between providers.
        User user = userRepository.findByEmail(email).orElseGet(User::new);

        user.setEmail(email);
        user.setFirstName(firstName);
        if (lastName != null) {
            user.setLastName(lastName);
        }
        user.setAuthProvider(provider);
        user.setProviderUserId(providerUserId);
        user.setEmailVerified(true);
        if (user.getStatus() == null) {
            user.setStatus(UserStatus.ACTIVE);
        }
        if (user.getRoles().isEmpty()) {
            user.addRole(resolveDefaultRole());
        }

        return userRepository.save(user);
    }

    private AuthProvider mapProvider(String registrationId) {
        return switch (registrationId.toLowerCase()) {
            case "google" -> AuthProvider.GOOGLE;
            case "microsoft" -> AuthProvider.MICROSOFT;
            default -> throw new AuthException("Unsupported OAuth2 provider: " + registrationId);
        };
    }

    private Role resolveDefaultRole() {
        return roleRepository.findByName(DEFAULT_ROLE)
                .orElseGet(() -> roleRepository.save(new Role(DEFAULT_ROLE)));
    }

    private String stringAttr(Map<String, Object> attrs, String... keys) {
        for (String key : keys) {
            Object value = attrs.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }

    private String firstNonBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
