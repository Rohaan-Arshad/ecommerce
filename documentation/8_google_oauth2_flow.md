# 8 — "Login with Google" — Step by Step

This file traces exactly what happens when a user clicks **Continue with Google**,
from the button click to the dashboard.

---

## 8.1 The players

- **The browser** — the user's Chrome/Firefox.
- **Our server** — this Spring Boot app on `http://localhost:3000`.
- **Google** — the *identity provider* (a.k.a. "OAuth2 Authorization Server").
- **Google Cloud Console** — where the app is registered (client‑id, client‑secret,
  and the allowed redirect URI `http://localhost:3000/app-auth`).

OAuth2 with the **Authorization Code** flow means our server never sees the user's
Google password. Google authenticates the user and hands our server a short‑lived
**code**, which our server exchanges (server‑to‑server) for the user's profile.

---

## 8.2 The flow

```
(1) Browser: click "Continue with Google"  →  GET /oauth2/authorization/google
                                               (handled by Spring Security)
        │
        │ (2) Spring builds an "authorization request" (client-id, scope, state,
        │     redirect_uri=http://localhost:3000/app-auth) and stores it in a
        │     short-lived cookie (HttpCookieOAuth2AuthorizationRequestRepository).
        ▼
(3) Browser is redirected to Google's sign-in page (accounts.google.com).
        │
        │ (4) User logs in to Google and consents to share email + profile.
        ▼
(5) Google redirects the browser back to:
        http://localhost:3000/app-auth?code=<AUTH_CODE>&state=<STATE>
        │
        ▼
(6) Our server (Spring's OAuth2 filter, matching /app-auth) receives it:
        - checks "state" matches the one saved in the cookie (anti-CSRF)
        - resolves which provider this is (google) from the saved request
        - exchanges <AUTH_CODE> at Google's token endpoint for an access/ID token
        - calls Google's userinfo endpoint → gets { sub, email, given_name, ... }
        │
        ▼
(7) OAuth2LoginSuccessHandler runs:
        - OAuth2ProvisioningService.upsert("google", oAuth2User)
              → find or create the User row (authProvider = GOOGLE)
        - AuthService.buildAuthResponse(user)  → makes OUR JWT
        - JwtCookieService.write(...)          → JWT_TOKEN cookie
        - redirect to /dashboard
        │
        ▼
(8) Browser: GET /dashboard (with JWT_TOKEN cookie)
        - JwtAuthenticationFilter reads the cookie → sets identity
        - PageController.dashboard(...) loads the user → renders dashboard.html
```

---

## 8.3 Where each part lives in the code

| Step | Code responsible |
|------|------------------|
| (1) the button | `templates/login.html` / `index.html`: `<a href="/oauth2/authorization/google">` |
| (2) build + store request | Spring Security's `OAuth2AuthorizationRequestRedirectFilter` + our `HttpCookieOAuth2AuthorizationRequestRepository` (file 10) |
| (3)(4) Google sign‑in | Google's servers (nothing of ours) |
| (5) callback URL | configured by `redirect-uri=http://localhost:3000/app-auth` and `redirectionEndpoint().baseUri("/app-auth")` in `SecurityConfig` |
| (6) code→token→profile | Spring's `OAuth2LoginAuthenticationFilter` using the Google URLs Spring already knows |
| (7) provision + JWT | `OAuth2LoginSuccessHandler` + `OAuth2ProvisioningService` + `AuthService` (file 10) |
| (8) dashboard | `JwtAuthenticationFilter` + `PageController.dashboard` (files 5, 7, 11) |

---

## 8.4 What "scope" and "state" mean

- **scope = `openid,email,profile`** — the permissions we request from Google:
  - `openid` → we want an OpenID Connect sign‑in (an ID token identifying the user),
  - `email` → the user's email address,
  - `profile` → basic profile info (name, picture).
- **state** — a random value Spring generates per login attempt. Google echoes it
  back on the callback. Spring compares it with the value saved in the cookie; if
  they differ, the request is rejected. This blocks **CSRF** on the callback and is
  also how Spring links a callback to the right provider/registration.

---

## 8.5 Why Google is simpler to configure than Microsoft

Google is a **well‑known provider**. Spring Boot ships with Google's endpoint URLs
built in, so `application.properties` only needs the `registration.google.*` lines
(client‑id, secret, scope, redirect‑uri). We do **not** list Google's
authorization/token/userinfo URLs — Spring already has them. (Microsoft is not
built in under that name, so we spell out its URLs — see file 9.)

---

## 8.6 The one manual step (console)

For Google to accept the callback, the redirect URI must be registered:

> Google Cloud Console → APIs & Services → Credentials → your OAuth 2.0 Client ID
> → **Authorized redirect URIs** → add exactly `http://localhost:3000/app-auth`.

If it isn't an exact match you get **`Error 400: redirect_uri_mismatch`** (which we
hit earlier and fixed by adding that exact URI).
