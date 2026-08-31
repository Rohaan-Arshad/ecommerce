package com.ecommerce.service;

import com.ecommerce.entity.AuthProvider;
import com.ecommerce.entity.Role;
import com.ecommerce.entity.User;
import com.ecommerce.entity.UserStatus;
import com.ecommerce.exception.AuthException;
import com.ecommerce.repository.RoleRepository;
import com.ecommerce.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Admin-only operations over users: listing, viewing, changing roles and status.
 */
@Service
public class UserAdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public UserAdminService(UserRepository userRepository, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public User get(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new AuthException("User not found: " + id));
    }

    /** Simple counters for the admin dashboard cards. */
    @Transactional(readOnly = true)
    public Map<String, Long> stats() {
        List<User> all = userRepository.findAll();
        long admins = all.stream().filter(u -> hasRole(u, "ADMIN")).count();
        return Map.of(
                "total", (long) all.size(),
                "admins", admins,
                "local", all.stream().filter(u -> u.getAuthProvider() == AuthProvider.LOCAL).count(),
                "google", all.stream().filter(u -> u.getAuthProvider() == AuthProvider.GOOGLE).count(),
                "microsoft", all.stream().filter(u -> u.getAuthProvider() == AuthProvider.MICROSOFT).count(),
                "blocked", all.stream().filter(u -> u.getStatus() == UserStatus.BLOCKED).count()
        );
    }

    @Transactional
    public void promoteToAdmin(Long id) {
        User user = get(id);
        user.addRole(role("ADMIN"));
        userRepository.save(user);
    }

    @Transactional
    public void demoteToCustomer(Long id) {
        User user = get(id);
        user.getRoles().removeIf(r -> "ADMIN".equals(r.getName()));
        if (user.getRoles().isEmpty()) {
            user.addRole(role("CUSTOMER"));
        }
        userRepository.save(user);
    }

    @Transactional
    public void setBlocked(Long id, boolean blocked) {
        User user = get(id);
        user.setStatus(blocked ? UserStatus.BLOCKED : UserStatus.ACTIVE);
        userRepository.save(user);
    }

    private boolean hasRole(User user, String name) {
        return user.getRoles().stream().map(Role::getName).anyMatch(name::equals);
    }

    private Role role(String name) {
        return roleRepository.findByName(name)
                .orElseGet(() -> roleRepository.save(new Role(name)));
    }
}
