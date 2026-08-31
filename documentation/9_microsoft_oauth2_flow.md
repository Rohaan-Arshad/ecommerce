# 9 — "Login with Microsoft" — Step by Step (and why `/app-auth`)

Microsoft login uses the **same OAuth2 Authorization Code flow** as Google. This
file focuses on what's *different*: the manual provider configuration, and the
reason the whole app runs on port 3000 with the `/app-auth` callback.

---

## 9.1 The flow (same shape as Google)

```
(1) Browser: click "Continue with Microsoft" → GET /oauth2/authorization/microsoft
(2) Spring stores the authorization request in a cookie, redirects to Microsoft
        (login.microsoftonline.com/<tenant>/oauth2/v2.0/authorize)
(3) User signs in with their Microsoft account and consents
(4) Microsoft redirects back to http://localhost:3000/app-auth?code=...&state=...
(5) Spring exchanges the code at Microsoft's token endpoint
        (login.microsoftonline.com/<tenant>/oauth2/v2.0/token)
(6) Spring calls Microsoft Graph userinfo (graph.microsoft.com/oidc/userinfo)
        → { sub, email/preferred_username, name, given_name, family_name }
(7) OAuth2LoginSuccessHandler: provision the user (authProvider = MICROSOFT),
        mint OUR JWT, set the JWT_TOKEN cookie, redirect to /dashboard
(8) Browser GET /dashboard → JwtAuthenticationFilter authenticates → page renders
```

---

## 9.2 Why Microsoft needs *provider* settings and Google doesn't

Google is a built‑in "well‑known" provider in Spring Boot; Microsoft is not
registered under the name `microsoft`, so we must describe its endpoints ourselves.
That's the second block in `application.properties`:

```properties
# what our app is + what we request
spring.security.oauth2.client.registration.microsoft.client-id=4d71cf86-...
spring.security.oauth2.client.registration.microsoft.client-secret=...
spring.security.oauth2.client.registration.microsoft.provider=microsoft   # ← links to the block below
spring.security.oauth2.client.registration.microsoft.scope=openid,email,profile
spring.security.oauth2.client.registration.microsoft.authorization-grant-type=authorization_code
spring.security.oauth2.client.registration.microsoft.redirect-uri=http://localhost:3000/app-auth

# where Microsoft's endpoints actually are
spring.security.oauth2.client.provider.microsoft.authorization-uri=https://login.microsoftonline.com/<tenant>/oauth2/v2.0/authorize
spring.security.oauth2.client.provider.microsoft.token-uri=https://login.microsoftonline.com/<tenant>/oauth2/v2.0/token
spring.security.oauth2.client.provider.microsoft.jwk-set-uri=https://login.microsoftonline.com/<tenant>/discovery/v2.0/keys
spring.security.oauth2.client.provider.microsoft.user-info-uri=https://graph.microsoft.com/oidc/userinfo
spring.security.oauth2.client.provider.microsoft.user-name-attribute=sub
```

- `<tenant>` = the Azure Active Directory (Entra ID) directory id from the original
  project. It scopes login to that organisation's sign‑in endpoints.
- `jwk-set-uri` gives Spring the **public keys** to verify the signature on
  Microsoft's ID token.
- `user-name-attribute=sub` tells Spring that the `sub` field is the unique user id.

These are the credentials the requirement described as *"the existing Microsoft
authorization configuration from the project"* — we reuse the same client‑id,
secret and tenant, just wired through Spring Security's standard OAuth2 support
instead of the old manual `RestTemplate` code.

---

## 9.3 Why the app runs on port 3000 with a `/app-auth` callback

This is the most surprising part of the setup, so here is the full reasoning.

**The constraint:** the Azure app registration (`4d71cf86-…`) is managed elsewhere
and we could **not** add a new redirect URI to it. The only redirect URI already
registered on it is the one the original salary project used:

```
http://localhost:3000/app-auth
```

**What Microsoft requires:** the `redirect_uri` our app sends must *exactly* match
one registered on the Azure app — same scheme, host, **port**, and path. Otherwise
Microsoft returns **`AADSTS50011: redirect URI ... does not match`** (which we hit
when the app briefly ran on port 8080 with Spring's default
`/login/oauth2/code/microsoft` path).

**The fix — make Spring's callback BE that URL:**

1. Run the whole app on **port 3000** (`server.port=3000`).
2. Set the Microsoft `redirect-uri` to `http://localhost:3000/app-auth`.
3. Tell Spring to receive OAuth callbacks on that path:
   `SecurityConfig`: `.redirectionEndpoint(r -> r.baseUri("/app-auth"))`.

**Sharing the path with Google:** Spring's redirection endpoint is a single path.
Rather than give each provider a different callback, we point **both** Google and
Microsoft at `/app-auth`. Spring knows which provider a given callback belongs to
by looking up the saved authorization request via the `state` parameter — so one
shared callback path is perfectly fine. That's why the Google client also
registers `http://localhost:3000/app-auth`.

```
Google  ─┐
         ├─► both redirect to  http://localhost:3000/app-auth
Microsoft┘        │
                  └─► Spring uses "state" to know which one it was
```

**Net result:** no change was needed on the locked Azure app — we bent our app to
match its existing registered URI.

> If you ever *can* edit the Azure app, the cleaner setup is the Spring default:
> per‑provider paths `http://localhost:<port>/login/oauth2/code/google` and
> `.../microsoft`. You'd remove the `redirect-uri` overrides and the
> `redirectionEndpoint(...)` line, and register those two URIs in the consoles.

---

## 9.4 Reading the profile: `email` vs `preferred_username`

Microsoft's userinfo does not always return an `email` claim (it depends on the
account type). `OAuth2ProvisioningService` therefore looks for the email across
several possible fields — `email`, `mail`, `preferred_username`, `upn` — and uses
the first non‑blank one. That's why login works for different kinds of Microsoft
accounts. (Details in file 10.)
