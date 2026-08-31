# 2 — Architecture Overview

This file explains the *shape* of the application: the layers, the packages, and
how a single HTTP request travels from the browser to the database and back.

---

## 2.1 The layers

The app is organised into classic Spring Boot layers. Each layer only talks to
the layer directly below it.

```
Browser (Thymeleaf pages / REST client)
        │  HTTP
        ▼
┌──────────────────────────────────────────────┐
│  Security filter chain                         │  ← runs on EVERY request
│  (CSRF off, JwtAuthenticationFilter, OAuth2)   │
└──────────────────────────────────────────────┘
        │
        ▼
┌──────────────────────────────────────────────┐
│  Controllers                                   │  ← map URLs to code
│  PageController (HTML)  AuthRestController (JSON)│
└──────────────────────────────────────────────┘
        │
        ▼
┌──────────────────────────────────────────────┐
│  Services (business logic)                     │
│  AuthService, OAuth2ProvisioningService        │
└──────────────────────────────────────────────┘
        │
        ▼
┌──────────────────────────────────────────────┐
│  Repositories (Spring Data JPA)                │
│  UserRepository, RoleRepository                │
└──────────────────────────────────────────────┘
        │
        ▼
     MySQL  (ecommerce_db)
```

Supporting pieces that sit beside these layers:

- **Entities** (`User`, `Role`) — Java objects mapped to database tables.
- **DTOs** (`RegisterRequest`, `LoginRequest`, `AuthResponse`) — the shapes of
  data coming in from and going out to clients.
- **Security helpers** (`JwtService`, `JwtCookieService`, the filter, the cookie
  auth‑request repository) — everything JWT/OAuth related.
- **Config** (`SecurityConfig`, `PasswordConfig`, `OAuth2LoginSuccessHandler`) —
  wiring and beans.

---

## 2.2 The package map

```
com.ecommerce
├── EcommerceApplication.java          → the main() entry point
│
├── config/
│   ├── SecurityConfig.java            → the Spring Security filter chain
│   ├── PasswordConfig.java            → the BCrypt PasswordEncoder bean
│   └── OAuth2LoginSuccessHandler.java → runs after Google/Microsoft login
│
├── controller/
│   ├── PageController.java            → serves HTML pages + handles form posts
│   └── AuthRestController.java        → JSON API under /api/auth/**
│
├── dto/
│   ├── RegisterRequest.java           → incoming registration data
│   ├── LoginRequest.java              → incoming login data
│   └── AuthResponse.java              → outgoing token + profile
│
├── entity/
│   ├── User.java                      → the users table
│   ├── Role.java                      → the roles table
│   ├── AuthProvider.java              → enum: LOCAL | GOOGLE | MICROSOFT
│   └── UserStatus.java                → enum: ACTIVE | INACTIVE | BLOCKED
│
├── exception/
│   ├── AuthException.java             → thrown on bad login / duplicate email
│   └── ApiExceptionHandler.java       → turns exceptions into clean JSON
│
├── repository/
│   ├── UserRepository.java            → DB access for users
│   └── RoleRepository.java            → DB access for roles
│
├── security/
│   ├── JwtService.java                → creates & validates JWTs
│   ├── JwtCookieService.java          → reads/writes the JWT cookie
│   ├── JwtAuthenticationFilter.java   → authenticates each request from the JWT
│   └── HttpCookieOAuth2AuthorizationRequestRepository.java
│                                      → keeps the OAuth "in‑flight" data in a cookie
│
└── service/
    ├── AuthService.java               → local register/login + JWT packaging
    └── OAuth2ProvisioningService.java → creates/updates a DB user from Google/MS
```

Resources:

```
src/main/resources
├── application.properties             → all configuration
├── static/css/style.css               → the page styling
└── templates/                         → Thymeleaf HTML
    ├── index.html                     → "choose how to sign in"
    ├── login.html                     → local login + provider buttons
    ├── register.html                  → create account
    └── dashboard.html                 → shown after login
```

---

## 2.3 The key architectural decision: **stateless + JWT**

Traditional web apps keep a **session** on the server: after you log in, the
server remembers you in its memory and gives your browser a `JSESSIONID` cookie
that points back to that memory.

This app does **not** do that. It is **stateless**:

- The server keeps *no* memory of who is logged in.
- Instead, the proof of identity is the **JWT** itself, carried by the browser in
  an HttpOnly cookie called `JWT_TOKEN` (or, for API clients, in the
  `Authorization: Bearer …` header).
- On every request, a filter reads that token, checks its signature, and rebuilds
  the identity from scratch.

Why this matters for you:

- All three login methods (local, Google, Microsoft) converge on **one** identity
  mechanism: our JWT. The dashboard doesn't care how you logged in.
- Because there is no session, the OAuth2 "in‑flight" data (the temporary state
  during the redirect to Google/Microsoft) is stored in a **short‑lived cookie**
  instead of a session — that is what
  `HttpCookieOAuth2AuthorizationRequestRepository` is for.

The switch to stateless is configured in `SecurityConfig` with:

```java
.sessionManagement(session -> session
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
```

---

## 2.4 Two ways in, one way to stay logged in

```
            ┌─────────────── LOCAL ───────────────┐
Browser ──► POST /login (email, password)          │
            AuthService verifies BCrypt hash        │
            JwtService makes a JWT                   ├──► JWT stored in
                                                     │    JWT_TOKEN cookie
            ┌──────── GOOGLE / MICROSOFT ─────────┐  │
Browser ──► /oauth2/authorization/google           │  │
            (Spring redirects to provider)          │  │
            provider sends code back to /app-auth   │  │
            Spring exchanges code for profile       │  │
            OAuth2ProvisioningService saves the user │  │
            OAuth2LoginSuccessHandler makes a JWT ───┘  │
                                                        ▼
Every later request ──► JwtAuthenticationFilter reads JWT_TOKEN
                        → rebuilds identity → page/API responds
```

The rest of the docs walk through each path in detail.
