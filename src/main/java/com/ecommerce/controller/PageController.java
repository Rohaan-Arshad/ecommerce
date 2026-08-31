package com.ecommerce.controller;

import com.ecommerce.dto.LoginRequest;
import com.ecommerce.dto.RegisterRequest;
import com.ecommerce.dto.SessionUser;
import com.ecommerce.entity.User;
import com.ecommerce.exception.AuthException;
import com.ecommerce.security.LoginRedirectResolver;
import com.ecommerce.security.SessionManager;
import com.ecommerce.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Serves the Thymeleaf pages and handles the browser (form-based) local
 * register/login flow. Browser logins establish a server-side session (via
 * {@link SessionManager}) that remembers the user and can be invalidated on
 * logout — the JWT is used only by the REST API.
 */
@Controller
public class PageController {

    private final AuthService authService;
    private final SessionManager sessionManager;
    private final LoginRedirectResolver redirectResolver;

    public PageController(AuthService authService,
                          SessionManager sessionManager,
                          LoginRedirectResolver redirectResolver) {
        this.authService = authService;
        this.sessionManager = sessionManager;
        this.redirectResolver = redirectResolver;
    }

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String firstName,
                           @RequestParam(required = false) String lastName,
                           @RequestParam String email,
                           @RequestParam String password,
                           @RequestParam(required = false) String phone,
                           Model model) {
        try {
            authService.register(new RegisterRequest(firstName, lastName, email, password, phone));
            return "redirect:/login?registered";
        } catch (AuthException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("firstName", firstName);
            model.addAttribute("lastName", lastName);
            model.addAttribute("email", email);
            model.addAttribute("phone", phone);
            return "register";
        }
    }

    @PostMapping("/login")
    public String login(@RequestParam String email,
                        @RequestParam String password,
                        HttpServletRequest request,
                        HttpServletResponse response,
                        Model model) {
        try {
            User user = authService.authenticate(new LoginRequest(email, password));
            // Establish the server-side session (remembers the user, holds details).
            sessionManager.login(user, request, response);
            return "redirect:" + redirectResolver.homeFor(user);
        } catch (AuthException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("email", email);
            return "login";
        }
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpServletRequest request, Model model) {
        SessionUser user = sessionManager.current(request);

        // Admins get their own console instead of the customer dashboard.
        if (user != null && user.isAdmin()) {
            return "redirect:/admin/dashboard";
        }

        // Show the "main things" straight from the session.
        model.addAttribute("email", user != null ? user.email() : "");
        model.addAttribute("fullName", user != null ? user.fullName() : "");
        model.addAttribute("authProvider", user != null ? user.authProvider() : "UNKNOWN");
        model.addAttribute("roles", user != null ? user.roles() : java.util.Set.of());
        model.addAttribute("loginAt", user != null ? user.loginAt() : null);
        return "dashboard";
    }
}
