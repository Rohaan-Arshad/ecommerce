package com.ecommerce.config;

import com.ecommerce.security.HttpCookieOAuth2AuthorizationRequestRepository;
import com.ecommerce.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Central security configuration.
 * <ul>
 *   <li>Public: home, login/register pages, the local auth REST endpoints,
 *       static assets and the OAuth2 endpoints.</li>
 *   <li>Everything else (dashboard, /api/**) requires a valid principal, which
 *       is established either by our {@link JwtAuthenticationFilter} (cookie or
 *       Bearer token) or by an in-flight OAuth2 login.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final HttpCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler,
                          HttpCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.oAuth2LoginSuccessHandler = oAuth2LoginSuccessHandler;
        this.authorizationRequestRepository = authorizationRequestRepository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF disabled: the app is token-based (JWT) and the demo forms
                // post to stateless endpoints. Enable per-form tokens for prod.
                .csrf(AbstractHttpConfigurer::disable)
                // Browser logins use a server-side HTTP session (so logout can
                // truly end them). The REST API stays token-based via the Bearer
                // header, which needs no session.
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/", "/login", "/register",
                                "/api/auth/register", "/api/auth/login",
                                "/css/**", "/js/**", "/images/**", "/uploads/**", "/webjars/**",
                                "/error", "/favicon.ico"
                        ).permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**", "/app-auth").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth -> oauth
                        .loginPage("/login")
                        // Store the in-flight authorization request in a cookie
                        // (not the session) so the flow works while stateless.
                        .authorizationEndpoint(authz -> authz
                                .authorizationRequestRepository(authorizationRequestRepository))
                        // Both providers call back to this single path (which is
                        // already registered on the Azure app). Spring resolves
                        // which provider a callback belongs to via the state param.
                        .redirectionEndpoint(redirect -> redirect.baseUri("/app-auth"))
                        .successHandler(oAuth2LoginSuccessHandler)
                        .failureUrl("/login?error=oauth")
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/?logout")
                        .invalidateHttpSession(true)     // destroy the server-side session
                        .clearAuthentication(true)
                        .deleteCookies("JWT_TOKEN", "JSESSIONID")
                )
                // Browser page requests redirect to /login; API requests get 401.
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(
                                apiAuthenticationEntryPoint(),
                                apiRequestMatcher())
                        .accessDeniedHandler(new AccessDeniedHandlerImpl())
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** REST clients get a 401 (rather than a redirect to the HTML login page). */
    private BasicAuthenticationEntryPoint apiAuthenticationEntryPoint() {
        BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
        entryPoint.setRealmName("ecommerce-api");
        return entryPoint;
    }

    private RequestMatcher apiRequestMatcher() {
        return request -> request.getRequestURI().startsWith("/api/");
    }
}
