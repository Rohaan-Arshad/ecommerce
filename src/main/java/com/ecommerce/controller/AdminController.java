package com.ecommerce.controller;

import com.ecommerce.service.UserAdminService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Admin console. Every route here is restricted to ROLE_ADMIN by SecurityConfig
 * (/admin/**). Renders the admin pages (own layout with a left sidebar) and
 * handles user management actions.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserAdminService userAdminService;

    public AdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("active", "dashboard");
        model.addAttribute("stats", userAdminService.stats());
        return "admin/dashboard";
    }

    @GetMapping("/users")
    public String users(Model model) {
        model.addAttribute("active", "users");
        model.addAttribute("users", userAdminService.findAll());
        return "admin/users";
    }

    /** "See the user screen" — an admin viewing a single user's details. */
    @GetMapping("/users/{id}")
    public String userDetail(@PathVariable Long id, Model model) {
        model.addAttribute("active", "users");
        model.addAttribute("user", userAdminService.get(id));
        return "admin/user-detail";
    }

    @PostMapping("/users/{id}/promote")
    public String promote(@PathVariable Long id) {
        userAdminService.promoteToAdmin(id);
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/users/{id}/demote")
    public String demote(@PathVariable Long id) {
        userAdminService.demoteToCustomer(id);
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/users/{id}/block")
    public String block(@PathVariable Long id) {
        userAdminService.setBlocked(id, true);
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/users/{id}/unblock")
    public String unblock(@PathVariable Long id) {
        userAdminService.setBlocked(id, false);
        return "redirect:/admin/users/" + id;
    }
}
