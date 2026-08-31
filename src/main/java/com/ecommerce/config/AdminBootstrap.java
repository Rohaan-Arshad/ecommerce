package com.ecommerce.config;

import com.ecommerce.entity.AuthProvider;
import com.ecommerce.entity.Role;
import com.ecommerce.entity.User;
import com.ecommerce.entity.UserStatus;
import com.ecommerce.repository.RoleRepository;
import com.ecommerce.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Creates the very first ADMIN account on startup (if it does not already
 * exist), so there is always a way to get into the admin console. Credentials
 * come from application.properties (app.admin.*).
 */
@Configuration
public class AdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    @Bean
    public ApplicationRunner seedAdmin(UserRepository userRepository,
                                       RoleRepository roleRepository,
                                       PasswordEncoder passwordEncoder,
                                       @Value("${app.admin.email:admin@ecommerce.com}") String email,
                                       @Value("${app.admin.password:Admin@123}") String password,
                                       @Value("${app.admin.name:System Admin}") String name) {
        return (ApplicationArguments args) -> {
            String normalized = email.trim().toLowerCase();
            if (userRepository.existsByEmail(normalized)) {
                return;
            }
            Role adminRole = roleRepository.findByName("ADMIN")
                    .orElseGet(() -> roleRepository.save(new Role("ADMIN")));

            User admin = new User();
            admin.setFirstName(name);
            admin.setEmail(normalized);
            admin.setPassword(passwordEncoder.encode(password));
            admin.setAuthProvider(AuthProvider.LOCAL);
            admin.setStatus(UserStatus.ACTIVE);
            admin.setEmailVerified(true);
            admin.addRole(adminRole);
            userRepository.save(admin);

            log.info("Seeded initial ADMIN account: {}", normalized);
        };
    }
}
