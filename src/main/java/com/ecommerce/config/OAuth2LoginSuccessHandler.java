package com.ecommerce.config;

import com.ecommerce.entity.User;
import com.ecommerce.security.LoginRedirectResolver;
import com.ecommerce.security.SessionManager;
import com.ecommerce.service.OAuth2ProvisioningService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Runs after a successful Google/Microsoft login: provisions the user in our
 * database, then replaces the raw OAuth2 principal with our own server-side
 * session (email + roles + stored details) and redirects by role. Using a
 * session means logout truly ends the login.
 */
@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final OAuth2ProvisioningService provisioningService;
    private final SessionManager sessionManager;
    private final LoginRedirectResolver redirectResolver;

    public OAuth2LoginSuccessHandler(OAuth2ProvisioningService provisioningService,
                                     SessionManager sessionManager,
                                     LoginRedirectResolver redirectResolver) {
        this.provisioningService = provisioningService;
        this.sessionManager = sessionManager;
        this.redirectResolver = redirectResolver;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException, ServletException {

        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        String registrationId = token.getAuthorizedClientRegistrationId();
        OAuth2User oAuth2User = token.getPrincipal();

        User user = provisioningService.upsert(registrationId, oAuth2User);

        // Overwrite the OAuth2 session identity with our own (email + ROLE_*),
        // and store the user's main details in the session.
        sessionManager.login(user, request, response);

        getRedirectStrategy().sendRedirect(request, response, redirectResolver.homeFor(user));
    }
}
