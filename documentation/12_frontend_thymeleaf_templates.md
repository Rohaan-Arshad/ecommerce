# 12 — Frontend: Thymeleaf Templates & CSS

The frontend is intentionally simple (the project's focus is backend auth). It is
four HTML files rendered by **Thymeleaf** plus one stylesheet.

---

## 12.1 What Thymeleaf is

**Thymeleaf** is a server‑side template engine. A template is normal HTML with
special `th:*` attributes. When a controller returns a view name (e.g. `"login"`),
Thymeleaf loads `templates/login.html`, replaces the `th:*` attributes with real
values from the `Model`, and outputs plain HTML to the browser. The browser never
sees Thymeleaf syntax — only the finished HTML.

Every template starts with the namespace declaration so `th:*` is understood:

```html
<html lang="en" xmlns:th="http://www.thymeleaf.org">
```

Common attributes used here:

| Attribute | Meaning |
|-----------|---------|
| `th:href="@{/login}"` | Build a URL relative to the app's context (`@{...}` is URL syntax). |
| `th:action="@{/login}"` | The form's submit URL. |
| `th:value="${email}"` | Pre‑fill an input with a model value. |
| `th:text="${fullName}"` | Replace the element's text with a value. |
| `th:if="${error}"` | Render the element only if the condition is truthy. |
| `th:each="role : ${roles}"` | Loop over a collection. |
| `${param.registered}` | Read a query‑string parameter (e.g. `?registered`). |

---

## 12.2 `templates/index.html` — the "choose how to sign in" page

Route: `GET /` → `PageController.home()`.

Shows three choices and an optional "signed out" banner:

```html
<a class="btn provider" th:href="@{/oauth2/authorization/google}">Continue with Google</a>
<a class="btn provider" th:href="@{/oauth2/authorization/microsoft}">Continue with Microsoft</a>
<a class="btn" th:href="@{/login}">Sign in with email</a>
<a class="btn secondary" th:href="@{/register}">Create an account</a>

<div th:if="${param.logout}" class="alert ok">You have been signed out.</div>
```

- The two provider links point at `/oauth2/authorization/{provider}` — these are the
  **built‑in Spring Security URLs** that *start* the OAuth2 flow (step 1 in files
  8/9). We don't write any code for them; Spring provides them.
- The email/create links go to our own `/login` and `/register` pages.

## 12.3 `templates/login.html` — local login + provider buttons

Route: `GET /login` → `PageController.loginPage()`; form posts to `POST /login`.

```html
<div th:if="${param.registered}" class="alert ok">Account created. Please sign in.</div>
<div th:if="${param.error}"      class="alert error">Sign-in with the external provider failed. Try again.</div>
<div th:if="${error}" class="alert error" th:text="${error}">Error</div>

<form th:action="@{/login}" method="post">
    <input type="email"    name="email"    th:value="${email}" required autofocus/>
    <input type="password" name="password" required/>
    <button class="btn" type="submit">Sign in</button>
</form>
```

- Three possible banners: `?registered` (came from a successful sign‑up), `?error`
  (an OAuth failure redirected here by `SecurityConfig.failureUrl`), and `${error}`
  (a local login error set by `PageController.login`).
- The form submits `email` + `password` to `POST /login`; the field names match the
  `@RequestParam`s in the controller.
- The same page also offers the Google/Microsoft buttons.

## 12.4 `templates/register.html` — create account

Route: `GET /register`; form posts to `POST /register`.

```html
<div th:if="${error}" class="alert error" th:text="${error}">Error</div>
<form th:action="@{/register}" method="post">
    <input name="firstName" th:value="${firstName}" required/>
    <input name="lastName"  th:value="${lastName}"/>
    <input name="email" type="email" th:value="${email}" required/>
    <input name="phone" th:value="${phone}"/>
    <input name="password" type="password" minlength="6" required/>
    <button class="btn" type="submit">Create account</button>
</form>
```

- Fields map to `RegisterRequest`. `minlength="6"` mirrors the server‑side
  `@Size(min=6)` rule (the browser checks first; the server always re‑checks).
- On a server error (e.g. duplicate email), the controller re‑renders this page with
  `${error}` and the previously‑typed values so the user doesn't retype everything.

## 12.5 `templates/dashboard.html` — after login

Route: `GET /dashboard` → `PageController.dashboard(...)` (see file 11).

```html
<dd th:text="${fullName}">Jane Doe</dd>
<dd th:text="${email}">jane@example.com</dd>
<dd><span class="badge" th:text="${authProvider}">LOCAL</span></dd>
<dd>
    <span class="badge" th:each="role : ${roles}" th:text="${role.authority}">ROLE_CUSTOMER</span>
</dd>

<form th:action="@{/logout}" method="post">
    <button class="btn secondary" type="submit">Sign out</button>
</form>
```

- `${fullName}`, `${email}`, `${authProvider}`, `${roles}` are the model attributes
  the controller set. The placeholder text ("Jane Doe") is only visible when opening
  the raw file in a browser; Thymeleaf replaces it at render time.
- `th:each` loops the authorities and prints each as a badge (`ROLE_CUSTOMER`).
- "Sign out" posts to `/logout`, which Spring Security handles: it deletes the
  `JWT_TOKEN` cookie and redirects to `/?logout`.

---

## 12.6 `static/css/style.css`

A single dark‑theme stylesheet. Files under `static/` are served directly by Spring
at the root path, so `th:href="@{/css/style.css}"` resolves to
`/css/style.css`. It defines the card layout, buttons (including the `.provider`
buttons), form inputs, alert banners, and the `.badge` pills used on the dashboard.
It contains no logic — purely presentation — because the project's focus is the
backend.

---

## 12.7 Why plain form posts instead of JavaScript fetch?

The login/register forms use standard HTML `method="post"` submissions (not
`fetch`/AJAX). Reasons:

- It keeps the frontend dependency‑free and easy to read.
- The server can set the `JWT_TOKEN` cookie and issue a normal redirect to
  `/dashboard`, which "just works" in the browser.
- The JSON API (`/api/auth/**`) still exists for anyone who *does* want to build a
  JavaScript/mobile frontend later — same logic, different entry point.
