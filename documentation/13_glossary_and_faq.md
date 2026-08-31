# 13 — Glossary & FAQ

Plain‑English definitions of every term used in this project, plus answers to
common questions.

---

## 13.1 Glossary

**Annotation** — a `@Something` marker on a class/method/field that tells Spring or
Java to treat it specially (e.g. `@Service`, `@Entity`, `@GetMapping`).

**Authentication** — proving *who* you are (logging in).

**Authorization** — deciding *what* you're allowed to do (roles, access rules).

**Authority / GrantedAuthority** — a permission string Spring checks. We use roles
in the form `ROLE_CUSTOMER`.

**Bean** — an object created and managed by Spring's container. You declare a bean
with `@Component`/`@Service`/`@Repository`/`@Configuration` + `@Bean`, and Spring
"injects" it wherever it's needed.

**BCrypt** — a deliberately slow, salted one‑way password hashing algorithm. We
store the hash, never the raw password, and verify by re‑hashing.

**Claim** — a piece of data inside a JWT (e.g. `sub`, `roles`, `exp`).

**Controller** — a class that maps URLs to code. `@Controller` returns view names
(HTML); `@RestController` returns JSON.

**Cookie** — a small value the browser stores and sends back on each request. We use
`JWT_TOKEN` (holds the token) and, during login, `OAUTH2_AUTH_REQUEST`.

**CORS** — rules about which other websites may call your API from the browser. Not
central here (same‑origin), but Spring supports it.

**CSRF** — an attack where another site makes your browser submit a request using
your cookies. Relevant to session‑cookie apps; we disabled CSRF because we're
token/stateless and the demo forms post to stateless endpoints.

**DTO (Data Transfer Object)** — a small object defining the shape of data in/out
(`RegisterRequest`, `LoginRequest`, `AuthResponse`). Ours are Java `record`s.

**Entity** — a Java class mapped to a database table (`User` → `users`).

**Filter** — code that runs on every HTTP request before controllers. Our
`JwtAuthenticationFilter` is one.

**Hibernate / JPA** — JPA is the Java standard for mapping objects to tables;
Hibernate is the implementation Spring Boot uses.

**HttpOnly cookie** — a cookie JavaScript can't read, protecting it from theft via
XSS. Our `JWT_TOKEN` is HttpOnly.

**Injection (Dependency Injection)** — Spring passes required beans into a class
(usually via its constructor) instead of the class creating them.

**JWT (JSON Web Token)** — a signed, self‑contained token carrying identity claims.
See file 5.

**OAuth2** — the standard protocol behind "Login with Google/Microsoft". We use the
**Authorization Code** flow.

**OpenID Connect (OIDC)** — an identity layer on top of OAuth2 that adds a
standard **ID token** and userinfo. The `openid` scope requests it.

**Provider (identity provider)** — Google or Microsoft, who authenticate the user
for us.

**Record** — a compact, immutable Java class for holding data; auto‑generates the
constructor and accessors. Used for the DTOs.

**Repository** — an interface (extending `JpaRepository`) that Spring turns into
database queries from the method names.

**Scope** — the permissions requested from a provider (`openid`, `email`,
`profile`).

**Service** — a class holding business logic (`AuthService`,
`OAuth2ProvisioningService`).

**Session** — server‑side memory of a logged‑in user. This app is **stateless**, so
it does *not* use sessions for identity.

**State (OAuth)** — a random value tying a login start to its callback, preventing
CSRF and identifying which provider replied.

**Stateless** — the server keeps no per‑user memory between requests; the JWT is the
identity on every request.

**sub (subject)** — the unique id of the user in a token/profile.

**Thymeleaf** — the server‑side HTML template engine rendering our pages.

**upsert** — "update if it exists, otherwise insert" — what
`OAuth2ProvisioningService` does with the user row.

---

## 13.2 FAQ

**Q: Where are passwords stored, and in what form?**
In the `users.password` column, as a **BCrypt hash**. Raw passwords are never
stored or logged. OAuth users have `password = null`.

**Q: If it's stateless, how does the app "remember" me between pages?**
It doesn't remember you server‑side. Your browser resends the `JWT_TOKEN` cookie on
every request, and `JwtAuthenticationFilter` re‑establishes your identity from that
token each time.

**Q: What happens after 30 minutes?**
The token's `exp` claim passes, `JwtService.isValid` returns false, and you're
treated as logged out — the browser is redirected to `/login` (or an API call gets
401). Log in again to get a fresh token. Change `jwt.token.validity` to adjust.

**Q: Why do Google and Microsoft share the same `/app-auth` callback?**
Because the Azure app registration is locked to `http://localhost:3000/app-auth`,
we point both providers there and let Spring tell them apart via the `state`
parameter. Full reasoning in file 9.

**Q: Why is the app on port 3000 and not 8080?**
So the callback URL exactly matches the one already registered on the Azure app
(`http://localhost:3000/app-auth`). Providers reject any mismatch.

**Q: How do I add an ADMIN‑only page later?**
The JWT already carries roles as `ROLE_...` authorities. Add a rule in
`SecurityConfig`, e.g. `.requestMatchers("/admin/**").hasRole("ADMIN")`, and assign
the `ADMIN` role to the relevant users.

**Q: Where would a separate React/mobile frontend plug in?**
Against the JSON API under `/api/auth/**`. Log in via `POST /api/auth/login`, keep
the returned `token`, and send it as `Authorization: Bearer <token>` on later calls.
The same `JwtAuthenticationFilter` authenticates header‑based requests.

**Q: Why was Lombok removed?**
Its annotation processor didn't run under Java 25 in this environment, so the
generated getters/setters were missing at compile time. We wrote the accessors by
hand in `User`/`Role` for a stable build.

**Q: Why did the app fail to start once with a "cycle" error?**
The `PasswordEncoder` bean lived in `SecurityConfig`, creating a circular
dependency (`SecurityConfig → success handler → AuthService → PasswordEncoder →
SecurityConfig`). Moving it to `PasswordConfig` broke the cycle. See file 6.

**Q: Why did the dashboard once show `SCOPE_*` / `UNKNOWN` / a long random name?**
Before the app was made stateless, the OAuth **session** identity (raw provider
principal) leaked onto the dashboard instead of our JWT identity. Switching to
`STATELESS` + the cookie auth‑request repository fixed it so the dashboard always
reflects our provisioned user and JWT. See files 6 and 10.
