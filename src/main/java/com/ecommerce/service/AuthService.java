package com.ecommerce.service;

import com.ecommerce.dto.AuthResponse;
import com.ecommerce.dto.LoginRequest;
import com.ecommerce.dto.RegisterRequest;
import com.ecommerce.entity.AuthProvider;
import com.ecommerce.entity.Role;
import com.ecommerce.entity.User;
import com.ecommerce.entity.UserStatus;
import com.ecommerce.exception.AuthException;
import com.ecommerce.repository.RoleRepository;
import com.ecommerce.repository.UserRepository;
import com.ecommerce.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles local (email + password) registration and login, and builds the
 * JWT-bearing {@link AuthResponse} shared by the REST API and the web pages.
 */
@Service
public class AuthService {

    private static final String DEFAULT_ROLE = "CUSTOMER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public User register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new AuthException("An account with this email already exists.");
        }

        User user = new User();
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName() == null ? null : request.lastName().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setPhone(request.phone());
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(false);
        user.addRole(resolveDefaultRole());

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User authenticate(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException("Invalid email or password."));

        if (user.getAuthProvider() != AuthProvider.LOCAL || user.getPassword() == null) {
            throw new AuthException("This account uses " + user.getAuthProvider()
                    + " sign-in. Please use that option.");
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new AuthException("Invalid email or password.");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthException("This account is " + user.getStatus() + ".");
        }
        return user;
    }

    /** Issues a fresh JWT and packages it with basic profile data. */
    public AuthResponse buildAuthResponse(User user) {
        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
        String token = jwtService.generateToken(user.getEmail(), roleNames);
        return new AuthResponse(
                token,
                "Bearer",
                jwtService.getTokenValidityMs(),
                user.getEmail(),
                user.getFullName(),
                user.getAuthProvider().name(),
                roleNames
        );
    }

    private Role resolveDefaultRole() {
        return roleRepository.findByName(DEFAULT_ROLE)
                .orElseGet(() -> roleRepository.save(new Role(DEFAULT_ROLE)));
    }
}
