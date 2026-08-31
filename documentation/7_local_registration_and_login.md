# 7 — Local Registration & Login (email + password)

This file covers the classic sign‑up / sign‑in path: `AuthService` (the logic) plus
the two controllers that expose it — `PageController` (HTML forms) and
`AuthRestController` (JSON API).

---

## 7.1 `service/AuthService.java`

This is where the business rules live: create an account, verify a login, and
package a JWT.

### Imports

| Import | What it does |
|--------|--------------|
| `com.ecommerce.dto.*` | The `AuthResponse`, `LoginRequest`, `RegisterRequest` shapes. |
| `com.ecommerce.entity.*` | `AuthProvider`, `Role`, `User`, `UserStatus`. |
| `com.ecommerce.exception.AuthException` | Thrown on business errors. |
| `com.ecommerce.repository.RoleRepository` / `UserRepository` | DB access. |
| `com.ecommerce.security.JwtService` | Creates the JWT. |
| `org.springframework.security.crypto.password.PasswordEncoder` | Hashes / verifies passwords (the BCrypt bean). |
| `org.springframework.stereotype.Service` | Marks this as a service bean. |
| `org.springframework.transaction.annotation.Transactional` | Wraps a method in a DB transaction. |
| `java.util.Set` / `java.util.stream.Collectors` | Collect role names into a set. |

### Registration

```java
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
    user.setPassword(passwordEncoder.encode(request.password())); // ← BCrypt hash
    user.setPhone(request.phone());
    user.setAuthProvider(AuthProvider.LOCAL);
    user.setStatus(UserStatus.ACTIVE);
    user.setEmailVerified(false);
    user.addRole(resolveDefaultRole());                            // ← CUSTOMER
    return userRepository.save(user);
}
```

- The email is **normalised** (trimmed + lower‑cased) so `A@B.com` and `a@b.com`
  are the same account.
- We reject duplicates up front.
- **The password is never stored raw** — `passwordEncoder.encode(...)` produces a
  BCrypt hash, and only the hash is saved.
- The user is marked `LOCAL`, `ACTIVE`, and given the `CUSTOMER` role.
- `@Transactional` means the whole method runs in one DB transaction: if anything
  fails, nothing is written.

### Login (verification)

```java
@Transactional(readOnly = true)
public User authenticate(LoginRequest request) {
    String email = request.email().trim().toLowerCase();
    User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new AuthException("Invalid email or password."));

    if (user.getAuthProvider() != AuthProvider.LOCAL || user.getPassword() == null) {
        throw new AuthException("This account uses " + user.getAuthProvider() + " sign-in. Please use that option.");
    }
    if (!passwordEncoder.matches(request.password(), user.getPassword())) {
        throw new AuthException("Invalid email or password.");
    }
    if (user.getStatus() != UserStatus.ACTIVE) {
        throw new AuthException("This account is " + user.getStatus() + ".");
    }
    return user;
}
```

- Looks up the user by email; "not found" and "wrong password" return the **same**
  vague message on purpose (so attackers can't tell which emails exist).
- If the account was created via Google/Microsoft (no local password), we tell the
  user to use that provider instead.
- `passwordEncoder.matches(raw, hash)` re‑hashes the entered password and compares —
  we never decrypt the stored hash (BCrypt is one‑way).
- Blocks non‑`ACTIVE` accounts.
- `readOnly = true` — a hint that this transaction only reads.

### Building the JWT response (shared by web + API)

```java
public AuthResponse buildAuthResponse(User user) {
    Set<String> roleNames = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
    String token = jwtService.generateToken(user.getEmail(), roleNames);
    return new AuthResponse(token, "Bearer", jwtService.getTokenValidityMs(),
            user.getEmail(), user.getFullName(), user.getAuthProvider().name(), roleNames);
}
```

- Collects the user's role names, asks `JwtService` for a token, and wraps
  everything into an `AuthResponse`. This method is reused by local login, local
  registration, *and* the OAuth success handler — so all three paths produce the
  same kind of token.

```java
private Role resolveDefaultRole() {
    return roleRepository.findByName("CUSTOMER")
            .orElseGet(() -> roleRepository.save(new Role("CUSTOMER")));
}
```

- Fetches the `CUSTOMER` role (seeded by your SQL). If somehow missing, it creates
  it — defensive so registration never fails on a missing role.

---

## 7.2 `controller/PageController.java` — the HTML/form flow

This `@Controller` serves the Thymeleaf pages and handles the browser form posts.

### Imports (highlights)

| Import | What it does |
|--------|--------------|
| `com.ecommerce.dto.*`, `entity.User`, `exception.AuthException` | Types used in the methods. |
| `com.ecommerce.repository.UserRepository` | Load the user for the dashboard. |
| `com.ecommerce.security.JwtCookieService` | Write the JWT cookie after login. |
| `com.ecommerce.service.AuthService` | Do the actual register/login. |
| `jakarta.servlet.http.HttpServletResponse` | To add the cookie to the response. |
| `org.springframework.security.core.Authentication` | The current user, injected by Spring on the dashboard. |
| `org.springframework.stereotype.Controller` | This returns view names (HTML), not JSON. |
| `org.springframework.ui.Model` | Carries data from controller to the Thymeleaf template. |
| `org.springframework.web.bind.annotation.GetMapping` / `PostMapping` / `RequestParam` | Map HTTP GET/POST and read form fields. |

### Page routes (GET)

```java
@GetMapping("/")         → returns "index"     (view = templates/index.html)
@GetMapping("/login")    → returns "login"
@GetMapping("/register") → returns "register"
```

Returning a **String** like `"login"` tells Spring/Thymeleaf to render
`templates/login.html`.

### Register (POST)

```java
@PostMapping("/register")
public String register(@RequestParam String firstName, ... , Model model) {
    try {
        authService.register(new RegisterRequest(firstName, lastName, email, password, phone));
        return "redirect:/login?registered";
    } catch (AuthException e) {
        model.addAttribute("error", e.getMessage());
        model.addAttribute("firstName", firstName); // repopulate the form
        ...
        return "register";
    }
}
```

- Reads the form fields with `@RequestParam` (we use plain params instead of binding
  to a record, which keeps the Thymeleaf forms simple).
- On success → **redirect** to `/login?registered` (the login page shows "Account
  created").
- On failure → re‑show the register page with the error message and the values the
  user already typed.

### Login (POST)

```java
@PostMapping("/login")
public String login(@RequestParam String email, @RequestParam String password,
                    HttpServletResponse response, Model model) {
    try {
        User user = authService.authenticate(new LoginRequest(email, password));
        AuthResponse auth = authService.buildAuthResponse(user);
        cookieService.write(response, auth.token(), (int) (auth.expiresInMs() / 1000));
        return "redirect:/dashboard";
    } catch (AuthException e) {
        model.addAttribute("error", e.getMessage());
        model.addAttribute("email", email);
        return "login";
    }
}
```

- Verifies credentials, builds the JWT, writes it to the `JWT_TOKEN` cookie
  (`expiresInMs / 1000` converts the token lifetime to cookie seconds), then
  **redirects to `/dashboard`**.
- The redirect causes a fresh request; `JwtAuthenticationFilter` reads the new
  cookie and authenticates it, so the dashboard renders.

### Dashboard (GET)

```java
@GetMapping("/dashboard")
public String dashboard(Authentication authentication, Model model) {
    String email = authentication.getName();                 // from the JWT
    User user = userRepository.findByEmail(email).orElse(null);
    model.addAttribute("email", email);
    model.addAttribute("fullName", user != null ? user.getFullName() : email);
    model.addAttribute("authProvider", user != null ? user.getAuthProvider().name() : "UNKNOWN");
    model.addAttribute("roles", authentication.getAuthorities());
    return "dashboard";
}
```

- `Authentication` is injected by Spring — it's the object our JWT filter put into
  the security context. `authentication.getName()` is the **email** (the JWT
  subject).
- We load the full `User` from the DB to show the real name and provider, then hand
  everything to `dashboard.html` via the `Model`. (File 11 traces this end to end.)

---

## 7.3 `controller/AuthRestController.java` — the JSON API

Same operations, but for programmatic clients (Postman, a JS frontend, a mobile
app). Marked `@RestController`, so methods return JSON.

### Imports (highlights)

| Import | What it does |
|--------|--------------|
| `jakarta.validation.Valid` | Triggers DTO validation on the request body. |
| `org.springframework.http.HttpStatus` / `ResponseEntity` | Build responses with status codes. |
| `org.springframework.web.bind.annotation.*` | `@RestController`, `@RequestMapping`, `@PostMapping`, `@GetMapping`, `@RequestBody`. |
| `org.springframework.security.core.Authentication` | The current user for `/me`. |

### Endpoints

```java
@RequestMapping("/api/auth")
public class AuthRestController {

    @PostMapping("/register")  // body: RegisterRequest (JSON) → 201 + AuthResponse
    @PostMapping("/login")     // body: LoginRequest  (JSON) → 200 + AuthResponse
    @GetMapping("/me")         // returns the current email + roles
    @PostMapping("/logout")    // clears the JWT cookie
}
```

- `@Valid @RequestBody RegisterRequest request` — Spring parses the JSON body into
  the record **and** validates it (`@NotBlank`, `@Email`, …). Invalid input is
  caught by `ApiExceptionHandler` and returned as a tidy JSON error.
- Both `register` and `login` also **write the JWT cookie** (so the same endpoints
  work from a browser), while returning the token in the JSON body (so API clients
  can grab it and send `Authorization: Bearer <token>`).
- `/me` echoes back who you are — handy to test that a token works.
- `/logout` clears the cookie.

Example API login:

```bash
curl -i -X POST http://localhost:3000/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{"email":"a@b.com","password":"secret123"}'
# → 200, JSON with "token": "...", and a Set-Cookie: JWT_TOKEN=...
```
