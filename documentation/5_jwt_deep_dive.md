# 5 — JWT Deep Dive

This file explains **what a JWT is**, then walks through the three classes that
create, carry, and check it: `JwtService`, `JwtCookieService`, and
`JwtAuthenticationFilter`.

---

## 5.1 What is a JWT?

**JWT** = JSON Web Token. It is a small, signed string that carries facts about the
user ("claims"). It has three parts separated by dots:

```
eyJhbGciOiJIUzI1NiJ9 . eyJzdWIiOiJhQGIuY29tIiwicm9sZXMiOlsiQ1VTVE9NRVIiXX0 . 3x9f...signature
└──── header ────┘   └──────────────── payload (claims) ────────────────┘   └── signature ──┘
```

- **Header** — says which signing algorithm is used (here `HS256`).
- **Payload (claims)** — the data: who the user is (`sub` = subject = email), their
  roles, when the token was issued (`iat`) and when it expires (`exp`).
- **Signature** — a cryptographic stamp created with our **secret key**. If anyone
  changes a single character of the header or payload, the signature no longer
  matches and the token is rejected.

Key properties:

- **Self‑contained:** everything needed to identify the user is *inside* the token.
  The server does not need to look anything up to know who you are — this is what
  makes the app **stateless**.
- **Tamper‑proof, not secret:** anyone can *read* the payload (it's just Base64),
  but nobody can *forge* or *modify* it without the secret key. So never put
  passwords or secrets in a JWT.
- **Expiring:** ours lasts 30 minutes (`jwt.token.validity`).

**HS256** (what we use) means one shared secret both signs and verifies. That
secret is `jwt.secret.key` in `application.properties`.

---

## 5.2 `security/JwtService.java` — create & validate tokens

### Imports

| Import | What it does |
|--------|--------------|
| `io.jsonwebtoken.Claims` | Represents the payload (the set of claims) of a parsed token. |
| `io.jsonwebtoken.JwtException` | The exception thrown when a token is invalid/expired/tampered. |
| `io.jsonwebtoken.Jwts` | The main entry point of the JJWT library — used to build and parse tokens. |
| `io.jsonwebtoken.security.Keys` | Helper to turn our secret string into a cryptographic `SecretKey`. |
| `org.springframework.beans.factory.annotation.Value` | Injects a value from `application.properties`. |
| `org.springframework.stereotype.Component` | Marks this as a Spring bean so it can be injected elsewhere. |
| `javax.crypto.SecretKey` | The Java type for the signing key. |
| `java.nio.charset.StandardCharsets` | Converts the secret string to bytes using UTF‑8. |
| `java.util.Date` | JWT timestamps (`issuedAt`, `expiration`) use `Date`. |
| `java.util.List` / `Set` | Roles are handled as collections. |

### Constructor — building the key

```java
public JwtService(
        @Value("${jwt.secret.key}") String secret,
        @Value("${jwt.token.validity:1800000}") long tokenValidityMs) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.tokenValidityMs = tokenValidityMs > 0 ? tokenValidityMs : 1800000L;
}
```

- `@Value("${jwt.secret.key}")` pulls the secret from properties.
- `@Value("${jwt.token.validity:1800000}")` — the `:1800000` is a **default**: if
  the property is missing, use 30 minutes.
- `Keys.hmacShaKeyFor(...)` turns the 64‑character secret into a real `SecretKey`
  suitable for HS256. (HS256 requires at least 256 bits; our key is 512 bits.)

### Generating a token

```java
public String generateToken(String email, Set<String> roles) {
    long now = System.currentTimeMillis();
    return Jwts.builder()
            .subject(email)                       // "sub" claim = who
            .claim("roles", roles)                // custom claim = what they can do
            .issuedAt(new Date(now))              // "iat"
            .expiration(new Date(now + tokenValidityMs)) // "exp"
            .signWith(key)                        // sign with our secret
            .compact();                           // produce the final string
}
```

This is the modern JJWT 0.13 API (`subject(...)`, `signWith(key)`). It builds the
three‑part token described above. The **email** becomes the subject and the
**roles** are embedded so we don't need a DB call to know them later.

### Reading / validating a token

```java
public String extractEmail(String token) { return parseClaims(token).getSubject(); }

public List<String> extractRoles(String token) {
    Object roles = parseClaims(token).get("roles");
    return roles instanceof List<?> list ? (List<String>) list : List.of();
}

public boolean isValid(String token) {
    try { parseClaims(token); return true; }
    catch (JwtException | IllegalArgumentException e) { return false; }
}

private Claims parseClaims(String token) {
    return Jwts.parser()
            .verifyWith(key)                 // check the signature with our secret
            .build()
            .parseSignedClaims(token)        // throws if invalid/expired/tampered
            .getPayload();                   // the claims
}
```

- `parseClaims` verifies the signature **and** the expiry. If either fails it throws
  — which is why `isValid` simply tries to parse and returns `false` on any
  exception.
- `extractEmail` / `extractRoles` read individual claims back out.

---

## 5.3 `security/JwtCookieService.java` — carry the token in a cookie

Browsers can't easily attach an `Authorization` header to a normal page click, so
for the web pages we store the JWT in an **HttpOnly cookie** named `JWT_TOKEN`.

### Imports

| Import | What it does |
|--------|--------------|
| `jakarta.servlet.http.Cookie` | Represents an HTTP cookie. |
| `jakarta.servlet.http.HttpServletRequest` | The incoming request (to read cookies from). |
| `jakarta.servlet.http.HttpServletResponse` | The outgoing response (to add/clear cookies). |
| `org.springframework.stereotype.Component` | Spring bean. |
| `java.util.Arrays` / `Optional` | Search the cookie array safely. |

### What it does

```java
public static final String COOKIE_NAME = "JWT_TOKEN";

public void write(HttpServletResponse response, String token, int maxAgeSeconds) {
    Cookie cookie = new Cookie(COOKIE_NAME, token);
    cookie.setHttpOnly(true);   // JavaScript cannot read it → protects against XSS theft
    cookie.setPath("/");        // sent on every path
    cookie.setMaxAge(maxAgeSeconds); // lifetime in seconds
    response.addCookie(cookie);
}

public void clear(HttpServletResponse response) { /* same cookie, maxAge 0 → deletes it */ }

public Optional<String> read(HttpServletRequest request) {
    // find the JWT_TOKEN cookie among the request's cookies
}
```

- **HttpOnly** is important security: it means client‑side JavaScript can't read the
  token, so a cross‑site scripting bug can't steal it.
- `write` is called after a successful login (local *and* OAuth). `clear` is called
  on logout. `read` is used by the filter below.

---

## 5.4 `security/JwtAuthenticationFilter.java` — authenticate every request

This is the heart of the stateless model. It runs **once per request** (that's what
`OncePerRequestFilter` guarantees) and rebuilds the user's identity from the token.

### Imports

| Import | What it does |
|--------|--------------|
| `jakarta.servlet.FilterChain` | Lets us pass the request to the next filter. |
| `jakarta.servlet.ServletException` / `java.io.IOException` | Checked exceptions the filter may throw. |
| `jakarta.servlet.http.HttpServletRequest` / `HttpServletResponse` | The request/response. |
| `org.springframework.lang.NonNull` | Documents that arguments are never null. |
| `org.springframework.security.authentication.UsernamePasswordAuthenticationToken` | The "you are authenticated" object we place into the security context. |
| `org.springframework.security.core.authority.SimpleGrantedAuthority` | Represents one granted role/authority (e.g. `ROLE_CUSTOMER`). |
| `org.springframework.security.core.context.SecurityContextHolder` | Where Spring Security stores the current user for this request. |
| `org.springframework.security.web.authentication.WebAuthenticationDetailsSource` | Adds request details (IP, session id) to the auth object. |
| `org.springframework.stereotype.Component` | Spring bean. |
| `org.springframework.util.StringUtils` | Null‑safe string helpers (`hasText`). |
| `org.springframework.web.filter.OncePerRequestFilter` | Base class guaranteeing single execution per request. |
| `java.util.List` | The list of authorities. |

### The logic

```java
protected void doFilterInternal(request, response, filterChain) {
    String token = resolveToken(request);           // header OR cookie

    if (token != null
            && SecurityContextHolder.getContext().getAuthentication() == null
            && jwtService.isValid(token)) {

        String email = jwtService.extractEmail(token);
        List<SimpleGrantedAuthority> authorities = jwtService.extractRoles(token).stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();

        var authentication = new UsernamePasswordAuthenticationToken(email, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
    filterChain.doFilter(request, response);         // continue
}

private String resolveToken(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
        return header.substring(7);                  // API clients
    }
    return cookieService.read(request).orElse(null); // browser
}
```

Step by step:

1. **Find the token** — first look for `Authorization: Bearer <token>` (used by API
   clients / Postman); otherwise read the `JWT_TOKEN` cookie (used by the browser).
2. **Only if** there's a token, nobody is authenticated yet, and the token is valid…
3. …extract the **email** (subject) and **roles**, and turn each role into a
   `ROLE_<name>` authority (Spring's convention — `ROLE_CUSTOMER`).
4. Build a `UsernamePasswordAuthenticationToken` (principal = email) and place it in
   the `SecurityContextHolder`. From this moment, for the rest of the request, the
   user is "logged in".
5. `filterChain.doFilter(...)` passes control to the next filter / the controller.

If the token is missing or invalid, we simply don't set an authentication — and the
security rules (file 6) will then redirect the browser to `/login` or return 401 to
an API client.

> **This is why the app is stateless:** identity is rebuilt from the token on every
> single request. Restart the server, load‑balance across many servers — it doesn't
> matter, because nothing about "who is logged in" is stored on the server.
