# 11 — Request Flow: Frontend ⇆ Backend (what happens after login)

This file answers directly: **after you log in, where does the request go, and how
does the dashboard data get produced and shown?** It traces one full round trip for
each login type and explains what is "frontend" and what is "backend" here.

---

## 11.1 First, what is "frontend" and "backend" in this project?

This app is **server‑rendered**. There is no separate React/Angular frontend. The
"frontend" is the **Thymeleaf HTML** produced *by the server*:

- **Backend** = the Spring Boot Java code (controllers, services, repositories,
  security filters) + MySQL.
- **Frontend** = the HTML/CSS the backend generates from the `templates/*.html`
  files and sends to the browser.

So the "request going to the frontend" really means: the browser asks the backend
for a URL, the backend runs Java, fills an HTML template with data, and returns the
finished HTML. The browser just displays it.

```
Browser ──HTTP request──► Backend (Java) ──reads──► MySQL
Browser ◄──HTML/JSON──── Backend (Java) ◄──rows───┘
```

---

## 11.2 The dashboard round trip (the key example)

After any login, the browser ends up requesting **`GET /dashboard`** carrying the
`JWT_TOKEN` cookie. Here is every step:

```
BROWSER                         BACKEND (Spring Boot)                         MYSQL
   │ GET /dashboard
   │ Cookie: JWT_TOKEN=eyJ...  ───►
   │                            (A) Security filter chain runs
   │                            (B) JwtAuthenticationFilter:
   │                                - reads JWT_TOKEN cookie
   │                                - JwtService.isValid(token)?  (checks signature + expiry)
   │                                - extractEmail() → "a@b.com"
   │                                - extractRoles() → ["CUSTOMER"]
   │                                - puts UsernamePasswordAuthenticationToken
   │                                  (principal=email, authorities=[ROLE_CUSTOMER])
   │                                  into SecurityContextHolder
   │                            (C) authorizeHttpRequests: /dashboard needs auth → OK
   │                            (D) PageController.dashboard(Authentication, Model):
   │                                - email = authentication.getName()      ("a@b.com")
   │                                - userRepository.findByEmail(email) ──────────────► SELECT * FROM users
   │                                                                     ◄────────────── the User row (+roles)
   │                                - model.addAttribute("fullName", user.getFullName())
   │                                - model.addAttribute("email", email)
   │                                - model.addAttribute("authProvider", user.getAuthProvider())
   │                                - model.addAttribute("roles", authentication.getAuthorities())
   │                                - return "dashboard"
   │                            (E) Thymeleaf renders templates/dashboard.html,
   │                                substituting ${fullName}, ${email}, ${authProvider}, ${roles}
   │ ◄── 200 OK, full HTML ─────
   │ (browser paints the page)
```

So the dashboard data comes from **two** sources, combined in step (D):

1. **The JWT** (already in the request) → the email and the roles/authorities.
2. **The database** (one `SELECT` by email) → the full name and the auth provider.

There is **no separate API call** from the page to fetch data — the backend has
everything before it renders, and ships a complete HTML page. That's why the
dashboard appears instantly with no "loading" state.

---

## 11.3 How the token got into that cookie (per login type)

### Local login
```
Browser POST /login (email, password)
  → PageController.login()
      → AuthService.authenticate()  → verifies BCrypt hash against MySQL
      → AuthService.buildAuthResponse() → JwtService.generateToken()
      → JwtCookieService.write() sets Set-Cookie: JWT_TOKEN=...
      → HTTP 302 redirect to /dashboard
Browser follows redirect → GET /dashboard (with the new cookie) → §11.2
```

### Google / Microsoft login
```
Browser GET /oauth2/authorization/google  (or .../microsoft)
  → Spring redirects to the provider; user authenticates there
Provider redirects Browser → GET /app-auth?code=...&state=...
  → Spring exchanges code → gets profile
  → OAuth2LoginSuccessHandler:
        OAuth2ProvisioningService.upsert()  → INSERT/UPDATE users in MySQL
        AuthService.buildAuthResponse()     → JwtService.generateToken()
        JwtCookieService.write()            → Set-Cookie: JWT_TOKEN=...
        HTTP 302 redirect to /dashboard
Browser follows redirect → GET /dashboard (with the cookie) → §11.2
```

In **all three** cases the end state is identical: a `JWT_TOKEN` cookie holding our
token, and `/dashboard` rendering from it. That uniformity is the whole point of the
design.

---

## 11.4 The JSON API path (for non‑browser clients)

If instead of the browser you use Postman / `curl` / a JS app:

```
POST /api/auth/login   { "email": "...", "password": "..." }
  → AuthRestController.login()
      → AuthService.authenticate() → buildAuthResponse()
      → returns 200 with JSON:
        { "token":"eyJ...", "tokenType":"Bearer", "expiresInMs":1800000,
          "email":"...", "fullName":"...", "authProvider":"LOCAL", "roles":["CUSTOMER"] }
      (also sets the JWT_TOKEN cookie)

Then call a protected endpoint with the header:
GET /api/auth/me
Authorization: Bearer eyJ...
  → JwtAuthenticationFilter authenticates from the header (not the cookie)
  → returns { "email":"...", "roles":[ ... ] }
```

Same filter, same token — it just reads the `Authorization` header instead of the
cookie. This is how a future separate frontend (React, mobile) would talk to this
backend.

---

## 11.5 What happens when the token is missing or expired

- **No/invalid token, browser hits `/dashboard`** → `JwtAuthenticationFilter` sets
  no identity → `authorizeHttpRequests` denies → the authentication entry point
  **redirects to `/login`**.
- **No/invalid token, client hits `/api/...`** → same denial, but the API entry
  point returns **HTTP 401** (no redirect), because API clients want a status code,
  not an HTML page.
- **Expired token** → `JwtService.isValid` returns `false` (the `exp` claim is in
  the past) → treated exactly like "no token" → back to `/login` / `401`. The user
  must log in again (our tokens last 30 minutes).

---

## 11.6 One‑glance summary

| Question | Answer |
|----------|--------|
| Where does the browser send the request? | To the Spring Boot backend on `http://localhost:3000`. |
| What proves who you are? | The `JWT_TOKEN` cookie (browser) or `Authorization: Bearer` header (API). |
| Who reads the token? | `JwtAuthenticationFilter`, on every request. |
| Where does dashboard data come from? | The JWT (email, roles) + one DB `SELECT` (name, provider). |
| Who builds the HTML? | The backend, via Thymeleaf, before responding. |
| Is there a session? | No — the app is stateless; the token is the only state. |
