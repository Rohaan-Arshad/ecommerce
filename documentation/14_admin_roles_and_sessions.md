# 14 — Admin, Roles & Sessions

This file covers the admin console added on top of the auth backend: how roles
work, how the first admin is created, how you promote a user, how admin and
customer screens are separated, and how "session" works in this stateless app.

---

## 14.1 Roles model

- Roles live in the `roles` table (`CUSTOMER`, `ADMIN`) and link to users via
  `user_roles` (see `User.roles`, file 4).
- The JWT carries the role names as a `roles` claim. `JwtAuthenticationFilter`
  turns each into a Spring authority `ROLE_<name>` (e.g. `ROLE_ADMIN`).
- `SecurityConfig` restricts the console:
  ```java
  .requestMatchers("/admin/**").hasRole("ADMIN")   // needs authority ROLE_ADMIN
  ```
  `hasRole("ADMIN")` automatically expects the `ROLE_` prefix.

## 14.2 How the first admin exists — `AdminBootstrap`

You can't promote anyone if there is no admin to start with, so
`config/AdminBootstrap.java` seeds one on startup **if it doesn't already exist**,
using properties:

```properties
app.admin.email=admin@ecommerce.com
app.admin.password=Admin@123
app.admin.name=System Admin
```

It's an `ApplicationRunner` bean: on boot it checks `existsByEmail`, and if absent
creates a `LOCAL`, `ACTIVE`, BCrypt-hashed user with the `ADMIN` role. Change these
properties (ideally to env vars) for real use. Log in at `/login` with those
credentials to reach the console.

## 14.3 How to make a user an admin

Two ways:

1. **From the UI** (as an existing admin): `/admin/users` → click **View** on a user
   → **Make admin**. This POSTs to `/admin/users/{id}/promote`.
2. **Directly in the DB** (bootstrap/first time): insert a row into `user_roles`
   linking the user's id to the `ADMIN` role id.

`UserAdminService.promoteToAdmin(id)` adds the `ADMIN` role;
`demoteToCustomer(id)` removes it (falling back to `CUSTOMER` so a user is never
role-less). `setBlocked(id, true/false)` flips `status` between `BLOCKED`/`ACTIVE`.

> A role change takes effect the next time that user logs in, because their **current
> JWT** still carries the old roles until it expires (max 30 min). This is normal for
> stateless JWT systems.

## 14.4 Separate admin vs customer screens

Routing by role happens at login. `LoginRedirectResolver.homeFor(user)` returns
`/admin/dashboard` for admins and `/dashboard` for everyone else. It is used by:

- `PageController.login` (local login),
- `OAuth2LoginSuccessHandler` (Google/Microsoft login).

Extra safety: if an admin hits the customer `/dashboard`, `PageController.dashboard`
redirects them to `/admin/dashboard`. And `/admin/**` is blocked for non-admins by
Spring Security, so a customer typing the URL gets denied.

```
login (any method) ──► LoginRedirectResolver
                         ├─ ADMIN     → /admin/dashboard  (sidebar console)
                         └─ CUSTOMER  → /dashboard        (simple account page)
```

## 14.5 The admin console (left-menu layout)

- `AdminController` (`@RequestMapping("/admin")`) serves the pages, all under the
  `ROLE_ADMIN` rule.
- Templates live in `templates/admin/`:
  - `fragments.html` → the **left sidebar** menu (`sidebar(active)` fragment). Each
    page includes it with `th:replace="~{admin/fragments :: sidebar(${active})}"`,
    passing which item is active so it highlights.
  - `dashboard.html` → stat cards (total/admins/local/google/microsoft/blocked) from
    `UserAdminService.stats()`.
  - `users.html` → table of all users with a **View** link.
  - `user-detail.html` → one user's details **plus admin action buttons**
    (make/remove admin, block/unblock). This is how an **admin sees a user's
    screen/details**.
- Styling: the `body.admin` + `.admin-shell` + `.sidebar` + `.main` rules in
  `style.css` create the fixed left menu and content area.

### Adding a new admin menu item (quick recipe)
1. Add a method + `@GetMapping("/admin/<thing>")` in `AdminController`, set
   `model.addAttribute("active", "<thing>")`, return `"admin/<thing>"`.
2. Add a `<a th:href="@{/admin/<thing>}" th:classappend="${active=='<thing>'}?'active'">`
   link in `fragments.html`.
3. Create `templates/admin/<thing>.html` (copy an existing page's shell).

## 14.6 Sessions (server-side) — the current model

> **This supersedes earlier "fully stateless" descriptions in files 2, 5, 6, 11.**
> The browser now uses **server-side HTTP sessions**; the JWT is kept **only for the
> REST API**. This was changed because a bare JWT does *not* expire on logout — a
> copied token stays valid until its `exp`. A session can be invalidated instantly.

**Two authentication paths now:**

| Client | How it's authenticated | How logout ends it |
|--------|------------------------|--------------------|
| Browser (pages) | Server-side `HttpSession` (`JSESSIONID` cookie) | `session.invalidate()` → immediate |
| REST API | `Authorization: Bearer <JWT>` | client drops the token (JWT still expires by `exp`) |

**What's stored in the session:** a `SessionUser` record — the user's *main details*:
`id`, `fullName`, `email`, `authProvider`, `roles`, and `loginAt` (session start
time). It's placed in the session at login and read by the dashboard.

**The moving parts (new/changed):**

- `dto/SessionUser.java` — the serializable snapshot kept in the session.
- `security/SessionManager.java` — `login(user, req, res)` stores the Spring
  `SecurityContext` **and** the `SessionUser` in the session; `current(req)` reads
  it back; `logout(req)` invalidates it.
- `PageController.login` and `OAuth2LoginSuccessHandler` now call
  `sessionManager.login(...)` instead of writing a JWT cookie.
- `PageController.dashboard` reads `SessionUser` from the session and shows it.
- `SecurityConfig`: session policy is `IF_REQUIRED` (was `STATELESS`); logout uses
  `.invalidateHttpSession(true).clearAuthentication(true)`.
- `JwtAuthenticationFilter` now reads **only** the `Authorization` header (no cookie),
  and only acts when there is no session authentication — i.e. it serves API clients.

**Lifecycle:**

- **Start:** login → `SessionManager.login` → session created, `SecurityContext` +
  `SessionUser` stored, `JSESSIONID` cookie sent to the browser.
- **Each request:** Spring loads the `SecurityContext` from the session automatically;
  the user is authenticated with their `ROLE_*` authorities.
- **End (logout):** `/logout` → Spring invalidates the session (drops the
  `SecurityContext` **and** `SessionUser`) and clears cookies → the user is *really*
  logged out; no leftover credential can authenticate the browser.
- **Duration:** the session lives until logout or servlet-container session timeout
  (configurable with `server.servlet.session.timeout`, e.g. `30m`).

**Why the API still uses JWT:** stateless tokens are ideal for programmatic clients
(mobile/JS/Postman) that manage their own token. Those tokens are still not
individually revocable before `exp`; if you need that, add a small server-side
denylist keyed by a token id (`jti`) checked in `JwtAuthenticationFilter`.

## 14.7 How to call it (quick test)

1. Start the app, open <http://localhost:3000/login>.
2. Log in as `admin@ecommerce.com` / `Admin@123` → lands on `/admin/dashboard`.
3. Go to **Users**, open any customer, click **Make admin** / **Block**.
4. Register a normal user → they land on `/dashboard` and cannot open `/admin/**`.

REST equivalents (admin JWT required as `Authorization: Bearer` or cookie) are not
exposed for admin actions in this version — the console uses server-rendered forms.
Add an `@RestController` under `/api/admin/**` the same way if you need an API.
