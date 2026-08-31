package com.ecommerce.security;

import com.ecommerce.dto.SessionUser;
import com.ecommerce.entity.Role;
import com.ecommerce.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Establishes and tears down the server-side session for a logged-in user.
 *
 * <p>On login we (1) place a Spring Security {@link SecurityContext} into the
 * HTTP session so every later request is authenticated from the session, and
 * (2) store a {@link SessionUser} with the user's main details. On logout we
 * invalidate the session, which is what actually ends the login (unlike a bare
 * JWT, which stays valid until it expires).</p>
 */
@Component
public class SessionManager {

    public static final String SESSION_USER = "SESSION_USER";

    private final SecurityContextRepository contextRepository =
            new HttpSessionSecurityContextRepository();

    /** Logs the user in for the browser: session context + stored details. */
    public SessionUser login(User user, HttpServletRequest request, HttpServletResponse response) {
        List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(Role::getName)
                .map(name -> new SimpleGrantedAuthority("ROLE_" + name))
                .toList();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user.getEmail(), null, authorities);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        // Persist the security context into the HTTP session.
        contextRepository.saveContext(context, request, response);

        SessionUser sessionUser = SessionUser.from(user);
        request.getSession(true).setAttribute(SESSION_USER, sessionUser);
        return sessionUser;
    }

    /** The main user details stored in the current session, or null. */
    public SessionUser current(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (SessionUser) session.getAttribute(SESSION_USER);
    }

    /** Ends the session completely. */
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
