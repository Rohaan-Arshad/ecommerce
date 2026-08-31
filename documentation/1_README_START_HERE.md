# 1 — README / Start Here

Welcome to the documentation for the **E‑Commerce Authentication Backend**.

This project is a Spring Boot application whose single job is **authentication**. A
user can sign in three ways:

1. **Local** — register with email + password, then log in.
2. **Google** — "Continue with Google" (OAuth2 / OpenID Connect).
3. **Microsoft** — "Continue with Microsoft" (OAuth2 / OpenID Connect).

After any of these succeeds, the app issues its **own JWT (JSON Web Token)** and
shows a simple dashboard page built with Thymeleaf.

---

## How to read this documentation

Read the files in order. Each one builds on the previous.

| File | What it covers |
|------|----------------|
| `1_README_START_HERE.md` | This file — how to run, the big picture, glossary of the most important words |
| `2_architecture_overview.md` | The layers of the app and how a request travels through them |
| `3_dependencies_pom_and_properties.md` | Every dependency in `pom.xml` and every line in `application.properties` |
| `4_domain_model_entities_repositories.md` | `User`, `Role`, the enums, repositories and DTOs — with every import explained |
| `5_jwt_deep_dive.md` | What a JWT is, and every line of `JwtService`, `JwtCookieService`, `JwtAuthenticationFilter` |
| `6_security_configuration.md` | `SecurityConfig` and `PasswordConfig` — the Spring Security filter chain |
| `7_local_registration_and_login.md` | Register + login with email/password (`AuthService`, controllers) |
| `8_google_oauth2_flow.md` | Step‑by‑step "Login with Google" |
| `9_microsoft_oauth2_flow.md` | Step‑by‑step "Login with Microsoft" and why we use `/app-auth` |
| `10_oauth2_provisioning_and_handlers.md` | `OAuth2ProvisioningService`, `OAuth2LoginSuccessHandler`, the cookie auth‑request repo |
| `11_request_flow_frontend_to_backend.md` | After login: where the request goes, and how the dashboard data is produced |
| `12_frontend_thymeleaf_templates.md` | The HTML pages and CSS |
| `13_glossary_and_faq.md` | Plain‑English definitions of every term and common questions |
| `14_admin_roles_and_sessions.md` | Admin console, roles, making a user an admin, admin/customer screen split, sessions |
| `15_product_and_image_management.md` | Product/variant/image data model, file-based image storage, admin product screens |

---

## How to run the project

**Prerequisites**

- Java 25
- MySQL 8+ running on `localhost:3306`
- The database `ecommerce_db` created using the provided SQL script (it creates
  the `users`, `roles`, `user_roles`, … tables and seeds the `CUSTOMER` and
  `ADMIN` roles).

**Start it**

```bash
mvnw clean spring-boot:run
```

Then open <http://localhost:3000/>.

> The app runs on **port 3000** on purpose — see `9_microsoft_oauth2_flow.md`
> for why (the OAuth callback URL has to match what is registered with the
> identity providers).

---

## The big picture in one paragraph

The browser talks to the Spring Boot server. For **local** login the server
checks your password (stored as a BCrypt hash) and, if it matches, creates a
**JWT** and stores it in an HttpOnly cookie. For **Google/Microsoft** login the
server hands the browser off to Google/Microsoft; they authenticate the user and
send the browser back to our server with a one‑time `code`; the server exchanges
that code for the user's profile, saves/updates the user in our database, and
then issues the **same kind of JWT** in the same cookie. Every page or API call
afterwards is authenticated by reading that JWT — there is **no server session**;
the token itself carries the identity.

---

## The most important words (mini‑glossary)

- **Authentication** — proving *who you are* (logging in).
- **Authorization** — deciding *what you may do* (roles/permissions).
- **JWT** — a signed, self‑contained token that carries your identity. See file 5.
- **OAuth2 / OpenID Connect** — the standard "Login with Google/Microsoft" protocol.
- **BCrypt** — a one‑way password hashing algorithm; we store hashes, never raw passwords.
- **Stateless** — the server keeps no per‑user memory between requests; the JWT is the memory.
- **Bean** — an object that Spring creates and manages for you.
- **Filter** — code that runs on *every* HTTP request before it reaches a controller.
- **Controller** — the class that handles a URL and returns a page or JSON.
- **Repository** — the interface that reads/writes the database.
- **Thymeleaf** — the template engine that turns `.html` files + data into the final page.

Full definitions are in `13_glossary_and_faq.md`.
