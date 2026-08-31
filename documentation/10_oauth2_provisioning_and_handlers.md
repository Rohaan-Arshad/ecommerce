# 10 — OAuth2 Provisioning & Handlers

Three classes make Google/Microsoft login integrate with *our* database and *our*
JWT. This file explains each, with imports.

- `OAuth2ProvisioningService` — turn a provider profile into a `User` row.
- `OAuth2LoginSuccessHandler` — runs after a successful provider login.
- `HttpCookieOAuth2AuthorizationRequestRepository` — the stateless "in‑flight"
  store.

---

## 10.1 `service/OAuth2ProvisioningService.java`

**Job:** after Google/Microsoft verifies the user, create the matching row in our
`users` table (or update it if they've logged in before). This guarantees every
authenticated user exists in our DB, exactly like a local user.

### Imports

| Import | What it does |
|--------|--------------|
| `com.ecommerce.entity.*` | `AuthProvider`, `Role`, `User`, `UserStatus`. |
| `com.ecommerce.exception.AuthException` | Thrown if the profile has no email or an unknown provider. |
| `com.ecommerce.repository.RoleRepository` / `UserRepository` | DB access. |
| `org.springframework.security.oauth2.core.user.OAuth2User` | The profile object Spring built from the provider's userinfo response. |
| `org.springframework.stereotype.Service` | Service bean. |
| `org.springframework.transaction.annotation.Transactional` | One DB transaction. |
| `java.util.Map` | The provider attributes come as a `Map<String,Object>`. |

### `upsert(...)` — the core method

("upsert" = update if exists, otherwise insert.)

```java
@Transactional
public User upsert(String registrationId, OAuth2User oAuth2User) {
    AuthProvider provider = mapProvider(registrationId);       // "google"→GOOGLE, "microsoft"→MICROSOFT
    Map<String,Object> attrs = oAuth2User.getAttributes();     // the raw profile fields

    String providerUserId = stringAttr(attrs, "sub", "id", "oid");
    String email = firstNonBlank(stringAttr(attrs, "email", "mail", "preferred_username", "upn"));
    if (email == null) throw new AuthException("The " + provider + " account did not provide an email address.");
    email = email.toLowerCase();

    String firstName = firstNonBlank(stringAttr(attrs, "given_name", "givenName"));
    String lastName  = firstNonBlank(stringAttr(attrs, "family_name", "surname"));
    String displayName = firstNonBlank(stringAttr(attrs, "name", "displayName"));
    if (firstName == null) firstName = displayName != null ? displayName : email;

    User user = userRepository.findByEmail(email).orElseGet(User::new); // reuse existing account by email

    user.setEmail(email);
    user.setFirstName(firstName);
    if (lastName != null) user.setLastName(lastName);
    user.setAuthProvider(provider);
    user.setProviderUserId(providerUserId);
    user.setEmailVerified(true);                               // provider already verified it
    if (user.getStatus() == null) user.setStatus(UserStatus.ACTIVE);
    if (user.getRoles().isEmpty()) user.addRole(resolveDefaultRole()); // CUSTOMER
    return userRepository.save(user);
}
```

Key points:

- **Different providers name fields differently**, so we search a list of candidate
  keys and take the first that has a value:
  - unique id → `sub` (Google/OIDC), `id`, or `oid` (Azure),
  - email → `email`, `mail`, `preferred_username`, or `upn` (Microsoft variance),
  - names → `given_name`/`givenName`, `family_name`/`surname`, `name`/`displayName`.
- We **match by email** first (`findByEmail`) so a person who once registered
  locally and later clicks "Login with Google" is *linked to the same row* rather
  than duplicated.
- `emailVerified = true` because the provider vouches for the address.
- New users get the `CUSTOMER` role.

### Small helpers

```java
private AuthProvider mapProvider(String registrationId) {
    return switch (registrationId.toLowerCase()) {
        case "google" -> AuthProvider.GOOGLE;
        case "microsoft" -> AuthProvider.MICROSOFT;
        default -> throw new AuthException("Unsupported OAuth2 provider: " + registrationId);
    };
}
private String stringAttr(Map<String,Object> attrs, String... keys) { /* first non-blank attribute */ }
private String firstNonBlank(String value) { /* null if blank */ }
private Role resolveDefaultRole() { /* CUSTOMER, create if missing */ }
```

---

## 10.2 `config/OAuth2LoginSuccessHandler.java`

**Job:** the bridge between "Spring finished the OAuth login" and "our app's JWT".
It runs *once*, immediately after Google/Microsoft authentication succeeds.

### Imports

| Import | What it does |
|--------|--------------|
| `com.ecommerce.dto.AuthResponse`, `entity.User` | The data it produces/uses. |
| `com.ecommerce.security.JwtCookieService` | Writes the JWT cookie. |
| `com.ecommerce.service.AuthService` | Builds the JWT (`buildAuthResponse`). |
| `com.ecommerce.service.OAuth2ProvisioningService` | Creates/updates the DB user. |
| `jakarta.servlet.ServletException`, `java.io.IOException` | Thrown by the servlet API. |
| `jakarta.servlet.http.HttpServletRequest` / `HttpServletResponse` | The request/response. |
| `org.springframework.security.core.Authentication` | The authentication result Spring passes in. |
| `org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken` | The specific auth type for an OAuth2 login — carries the registration id + profile. |
| `org.springframework.security.oauth2.core.user.OAuth2User` | The provider profile. |
| `org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler` | Base class that provides redirect helpers. |
| `org.springframework.stereotype.Component` | Spring bean. |

### The method

```java
@Override
public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                     Authentication authentication) throws IOException, ServletException {
    OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
    String registrationId = token.getAuthorizedClientRegistrationId();  // "google" or "microsoft"
    OAuth2User oAuth2User = token.getPrincipal();                       // the profile

    User user = provisioningService.upsert(registrationId, oAuth2User); // DB row
    AuthResponse auth = authService.buildAuthResponse(user);            // OUR JWT

    cookieService.write(response, auth.token(), (int) (auth.expiresInMs() / 1000)); // JWT_TOKEN cookie
    getRedirectStrategy().sendRedirect(request, response, "/dashboard");            // go to dashboard
}
```

- Spring calls this with an `OAuth2AuthenticationToken`. We read which provider it
  was (`getAuthorizedClientRegistrationId()`) and the profile (`getPrincipal()`).
- Provision the user → build the **same JWT** local login uses → store it in the
  `JWT_TOKEN` cookie → redirect to `/dashboard`.
- Crucially, from here on the browser is authenticated by **our JWT**, not by any
  OAuth session — which is why the dashboard shows our real user data. (Before we
  made the app stateless, the OAuth *session* identity leaked onto the dashboard and
  showed the raw `sub`/`SCOPE_*` values.)

---

## 10.3 `security/HttpCookieOAuth2AuthorizationRequestRepository.java`

**Job:** hold the temporary OAuth "authorization request" between step (2) starting
the login and step (6) the callback. Spring's default keeps this in the HTTP
**session** — but we are **stateless** (no session), so we keep it in a short‑lived
cookie instead.

### Imports

| Import | What it does |
|--------|--------------|
| `jakarta.servlet.http.Cookie` / `HttpServletRequest` / `HttpServletResponse` | Read and write cookies. |
| `org.springframework.security.oauth2.client.web.AuthorizationRequestRepository` | The interface we implement. |
| `org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest` | The object being stored (client id, scopes, state, redirect uri…). |
| `org.springframework.stereotype.Component` | Spring bean. |
| `org.springframework.util.SerializationUtils` | Turn the object into bytes and back. |
| `java.util.Arrays` / `Base64` / `Optional` | Encode/decode and search cookies. |

### What it stores and how

```java
private static final String COOKIE_NAME = "OAUTH2_AUTH_REQUEST";
private static final int COOKIE_EXPIRE_SECONDS = 180;   // 3 minutes is plenty for a login
```

- `saveAuthorizationRequest(...)` — serialises the `OAuth2AuthorizationRequest` to
  bytes, Base64‑URL‑encodes it, and writes it as an HttpOnly cookie
  `OAUTH2_AUTH_REQUEST` (3‑minute lifetime). If Spring passes `null`, it deletes the
  cookie.
- `loadAuthorizationRequest(...)` — reads that cookie back, Base64‑decodes and
  deserialises it into the object.
- `removeAuthorizationRequest(...)` — loads it (to hand back to Spring) and then
  deletes the cookie, because the login is finishing.

Spring uses this repository automatically because we registered it in
`SecurityConfig`:

```java
.authorizationEndpoint(authz -> authz
        .authorizationRequestRepository(authorizationRequestRepository))
```

### Why this is required

Without it, running `SessionCreationPolicy.STATELESS` would break OAuth login:
Spring would try to save the authorization request in a session that never exists,
lose the `state` value, and fail the callback. The cookie store lets us have **both**
a stateless app **and** working "Login with Google/Microsoft".
