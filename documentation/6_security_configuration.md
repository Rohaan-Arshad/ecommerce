# 6 — Security Configuration

This file explains `SecurityConfig` (the Spring Security filter chain) and
`PasswordConfig` (the password hasher), including every import and every option.

---

## 6.1 `config/PasswordConfig.java`

### Imports

| Import | What it does |
|--------|--------------|
| `org.springframework.context.annotation.Bean` | Marks a method that produces a Spring‑managed object. |
| `org.springframework.context.annotation.Configuration` | Marks the class as a source of beans. |
| `org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder` | The BCrypt implementation. |
| `org.springframework.security.crypto.password.PasswordEncoder` | The interface other classes depend on. |

### Body

```java
@Configuration
public class PasswordConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

- A **`PasswordEncoder`** hashes passwords one way. **BCrypt** is a slow,
  salted hashing algorithm designed for passwords — it is deliberately expensive so
  brute‑force guessing is hard, and it embeds a random salt so identical passwords
  produce different hashes.
- `AuthService` uses this bean to `encode(...)` a password at registration and to
  `matches(raw, hash)` at login.

### Why is this in its own file (and not in `SecurityConfig`)?

Originally the `PasswordEncoder` bean lived inside `SecurityConfig`. That created a
**circular dependency**:

```
SecurityConfig ──needs──► OAuth2LoginSuccessHandler ──needs──► AuthService ──needs──► PasswordEncoder
      ▲                                                                                     │
      └──────────────────────── (PasswordEncoder was defined in SecurityConfig) ◄──────────┘
```

Spring couldn't decide what to build first and refused to start
(`APPLICATION FAILED TO START … the dependencies form a cycle`). Moving
`PasswordEncoder` into its own `PasswordConfig` breaks the loop: now `AuthService`
depends on `PasswordConfig`, not on `SecurityConfig`.

---

## 6.2 `config/SecurityConfig.java`

This is the single most important configuration class. It defines the **security
filter chain** — the ordered list of filters every request passes through.

### Imports

| Import | What it does |
|--------|--------------|
| `com.ecommerce.security.HttpCookieOAuth2AuthorizationRequestRepository` | Our cookie store for the in‑flight OAuth request (needed because we're stateless). |
| `com.ecommerce.security.JwtAuthenticationFilter` | Our JWT filter (file 5) to plug into the chain. |
| `org.springframework.context.annotation.Bean` / `Configuration` | Bean/config markers. |
| `org.springframework.security.config.annotation.web.builders.HttpSecurity` | The builder used to describe all security rules. |
| `org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer` | Used to disable CSRF concisely. |
| `org.springframework.security.config.http.SessionCreationPolicy` | The enum with `STATELESS`. |
| `org.springframework.security.web.SecurityFilterChain` | The object we build and return. |
| `org.springframework.security.web.access.AccessDeniedHandlerImpl` | Default handler for "logged in but not allowed". |
| `org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter` | A built‑in filter; we insert ours *before* it. |
| `org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint` | Returns `401` for API requests instead of an HTML redirect. |
| `org.springframework.security.web.util.matcher.RequestMatcher` | A predicate that decides if a rule applies to a request. |

### Constructor (dependency injection)

```java
public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                      OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler,
                      HttpCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository) { ... }
```

Spring **injects** these three beans automatically (constructor injection). We then
wire them into the chain below.

### The filter chain — option by option

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/", "/login", "/register",
                        "/api/auth/register", "/api/auth/login",
                        "/css/**", "/js/**", "/images/**", "/webjars/**",
                        "/error", "/favicon.ico"
                ).permitAll()
                .requestMatchers("/oauth2/**", "/login/oauth2/**", "/app-auth").permitAll()
                .anyRequest().authenticated()
        )
        .oauth2Login(oauth -> oauth
                .loginPage("/login")
                .authorizationEndpoint(authz -> authz
                        .authorizationRequestRepository(authorizationRequestRepository))
                .redirectionEndpoint(redirect -> redirect.baseUri("/app-auth"))
                .successHandler(oAuth2LoginSuccessHandler)
                .failureUrl("/login?error=oauth")
        )
        .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/?logout")
                .deleteCookies("JWT_TOKEN", "JSESSIONID")
        )
        .exceptionHandling(ex -> ex
                .defaultAuthenticationEntryPointFor(apiAuthenticationEntryPoint(), apiRequestMatcher())
                .accessDeniedHandler(new AccessDeniedHandlerImpl())
        )
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
}
```

| Section | Meaning |
|---------|---------|
| `csrf(...disable)` | Turns off CSRF protection. CSRF tokens matter for session‑cookie apps; we're token‑based and stateless, and the demo forms post to stateless endpoints, so we disable it for simplicity. (For production you'd re‑enable CSRF for the browser forms.) |
| `sessionManagement(... STATELESS)` | **No HTTP session is created.** Identity comes only from the JWT. This is the core of the stateless design. |
| `authorizeHttpRequests` | The access rules, checked top to bottom. |
| `permitAll()` group 1 | Public pages/assets: home, login/register pages, the local auth JSON endpoints, static files, the error page, favicon. Anyone can reach these. |
| `permitAll()` group 2 | The OAuth2 machinery URLs: `/oauth2/**` (where "Login with Google" starts), `/login/oauth2/**`, and `/app-auth` (our callback). These must be open so the login can complete. |
| `anyRequest().authenticated()` | **Everything else** (e.g. `/dashboard`, other `/api/**`) requires a valid identity. |
| `oauth2Login(...)` | Configures "Login with Google/Microsoft" (see below). |
| `logout(...)` | `/logout` clears the `JWT_TOKEN` (and any leftover `JSESSIONID`) cookie and returns to home. |
| `exceptionHandling(...)` | For `/api/**` return HTTP `401` (via Basic entry point) so API clients get a clean status; for pages, the default entry point redirects to `/login`. `AccessDeniedHandlerImpl` handles the "authenticated but forbidden" case. |
| `addFilterBefore(jwtAuthenticationFilter, ...)` | **Inserts our JWT filter** into the chain, before the username/password filter, so identity is established from the token early. |

### The `oauth2Login` block in detail

- `.loginPage("/login")` — unauthenticated users are sent to our custom login page
  (not Spring's default generated one).
- `.authorizationEndpoint(...authorizationRequestRepository(...))` — tells Spring to
  store the temporary OAuth "authorization request" in our **cookie‑based** repo
  instead of the session. Required because we're stateless. (See file 10.)
- `.redirectionEndpoint(...baseUri("/app-auth"))` — the URL Google/Microsoft call
  back to. We use one shared path `/app-auth`; Spring figures out which provider a
  callback belongs to from the `state` parameter. (Why `/app-auth`? See file 9.)
- `.successHandler(oAuth2LoginSuccessHandler)` — after a successful provider login,
  run our handler (provision the user + issue the JWT). See file 10.
- `.failureUrl("/login?error=oauth")` — on failure, go back to login with an error.

### Helper methods

```java
private BasicAuthenticationEntryPoint apiAuthenticationEntryPoint() {
    var entryPoint = new BasicAuthenticationEntryPoint();
    entryPoint.setRealmName("ecommerce-api");
    return entryPoint;               // → responds 401 for API requests
}

private RequestMatcher apiRequestMatcher() {
    return request -> request.getRequestURI().startsWith("/api/");
}
```

- `apiRequestMatcher()` is a tiny predicate: "does the path start with `/api/`?"
  Requests that match get the 401 entry point; everything else gets the login
  redirect. (We wrote it as a lambda instead of `AntPathRequestMatcher` so it keeps
  working across Spring Security versions.)

---

## 6.3 How the rules protect `/dashboard`

1. Browser requests `/dashboard`.
2. `JwtAuthenticationFilter` runs. If the `JWT_TOKEN` cookie holds a valid token, it
   sets the authentication; otherwise it does nothing.
3. `authorizeHttpRequests` sees `/dashboard` is **not** in the permit lists, so it
   requires authentication.
4. If authenticated → the request reaches `PageController.dashboard(...)`.
   If not → the entry point redirects the browser to `/login`.
