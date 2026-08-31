# 3 — Dependencies (`pom.xml`) and Configuration (`application.properties`)

This file explains **every** dependency the project pulls in and **every** line of
configuration.

---

## 3.1 `pom.xml` — the build file

`pom.xml` is the Maven build descriptor. It tells Maven the project's identity,
which Java version to use, and which libraries (dependencies) to download.

### Parent

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.1.0</version>
</parent>
```

- **spring-boot-starter-parent** gives us sensible defaults and, crucially,
  *dependency version management*: for most libraries we don't specify a version
  because the parent already picks a tested, compatible one. Version `4.1.0` means
  we are on Spring Boot 4 (which runs on Spring Framework 7 and Jakarta EE 11).

### Identity & Java version

```xml
<groupId>com.ecommerce</groupId>
<artifactId>ecommerce</artifactId>
<version>0.0.1-SNAPSHOT</version>
<packaging>jar</packaging>
...
<java.version>25</java.version>
```

- **packaging = jar** → the build produces a runnable "fat jar" with an embedded
  Tomcat web server. (The original salary project was a `war`; we switched to
  `jar` so it runs standalone with `java -jar` or `spring-boot:run`.)

### Dependencies (each explained)

| Dependency | Why it is here |
|------------|----------------|
| `spring-boot-starter-webmvc` | The web framework — lets us write `@Controller`/`@RestController`, map URLs, serve HTTP. Brings in the embedded Tomcat server. |
| `spring-boot-starter-thymeleaf` | The server‑side template engine that renders our `.html` pages (login, register, dashboard). |
| `spring-boot-starter-data-jpa` | Database access via JPA/Hibernate + Spring Data repositories (`UserRepository`, etc.). |
| `mysql-connector-j` (v9.3.0) | The MySQL JDBC driver so JPA can talk to MySQL. |
| `spring-boot-starter-security` | Spring Security — the whole authentication/authorization framework (filter chain, password encoding, etc.). |
| `spring-boot-starter-oauth2-client` | The "Login with Google/Microsoft" support (OAuth2 / OpenID Connect *client* side). |
| `spring-boot-starter-validation` | Bean Validation (`@NotBlank`, `@Email`, `@Size`) used on the DTOs. |
| `jjwt-api` (0.13.0) | The JWT library's public API — how we *write* JWT code. |
| `jjwt-impl` (0.13.0, runtime) | The JWT library's implementation — needed at runtime, not compile time. |
| `jjwt-jackson` (0.13.0, runtime) | Lets the JWT library serialize/parse JSON using Jackson. |
| `spring-boot-starter-test` (test) | JUnit, Mockito, Spring test support for the test folder. |
| `spring-security-test` (test) | Helpers for testing secured endpoints. |

> **Note on Lombok:** the original project used Lombok to auto‑generate getters and
> setters. Lombok's annotation processor was not compatible with Java 25 in this
> environment (it silently failed, so the getters/setters "didn't exist" at compile
> time). We removed Lombok and wrote the accessors by hand in `User` and `Role`.
> This makes the build stable and the entities easier to read for beginners.

### Build plugin

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
</plugin>
```

- The **spring-boot-maven-plugin** packages everything into the runnable fat jar
  and enables `mvnw spring-boot:run`.

---

## 3.2 `application.properties` — configuration, line by line

This file lives in `src/main/resources` and configures the running app. Spring
reads it automatically at startup.

### Application name & server port

```properties
spring.application.name=ecommerce
server.port=3000
```

- `spring.application.name` — a label for the app (shows in logs).
- `server.port=3000` — the app listens on **port 3000**. This is deliberate: the
  OAuth2 redirect URI registered with the identity providers is
  `http://localhost:3000/app-auth`, and it must match exactly. (Details in file 9.)

### Database (MySQL)

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/ecommerce_db?zeroDateTimeBehavior=convertToNull
spring.datasource.username=root
spring.datasource.password=root123
spring.jpa.hibernate.ddl-auto=none
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.open-in-view=false
```

- `spring.datasource.url` — where the DB is. `ecommerce_db` is the schema; the
  `zeroDateTimeBehavior=convertToNull` part turns MySQL's `0000‑00‑00` dates into
  `null` instead of crashing.
- `username` / `password` — DB credentials.
- `ddl-auto=none` — **Hibernate will NOT create or modify tables.** The tables are
  created by your SQL script, and Hibernate just maps onto them. (Safe: it never
  drops your data.)
- `show-sql=false` — don't print every SQL statement (set `true` while debugging).
- `format_sql=true` — if SQL *is* printed, make it readable.
- `open-in-view=false` — closes the database connection at the end of the service
  layer instead of keeping it open during view rendering. This is the recommended,
  cleaner setting; it forces us to load what we need inside transactions.

### Thymeleaf

```properties
spring.thymeleaf.cache=false
```

- Disables template caching so edits to `.html` files show up without a restart.
  (In production you'd set this to `true` for speed.)

### Google OAuth2

```properties
spring.security.oauth2.client.registration.google.client-id=654622012319-...apps.googleusercontent.com
spring.security.oauth2.client.registration.google.client-secret=GOCSPX-...
spring.security.oauth2.client.registration.google.scope=openid,email,profile
spring.security.oauth2.client.registration.google.redirect-uri=http://localhost:3000/app-auth
```

- `client-id` / `client-secret` — credentials issued by Google Cloud Console that
  identify *our app* to Google.
- `scope=openid,email,profile` — what we ask Google for: the OpenID sign‑in, the
  user's email, and basic profile (name).
- `redirect-uri` — where Google sends the browser back after login. We use the
  shared `/app-auth` path (see file 9). Google itself is a **well‑known provider**,
  so Spring already knows Google's authorization/token/userinfo URLs — we don't
  list them.

### Microsoft OAuth2

```properties
spring.security.oauth2.client.registration.microsoft.client-id=4d71cf86-...
spring.security.oauth2.client.registration.microsoft.client-secret=vGs8Q~...
spring.security.oauth2.client.registration.microsoft.client-name=Microsoft
spring.security.oauth2.client.registration.microsoft.provider=microsoft
spring.security.oauth2.client.registration.microsoft.scope=openid,email,profile
spring.security.oauth2.client.registration.microsoft.authorization-grant-type=authorization_code
spring.security.oauth2.client.registration.microsoft.redirect-uri=http://localhost:3000/app-auth

spring.security.oauth2.client.provider.microsoft.authorization-uri=https://login.microsoftonline.com/<tenant>/oauth2/v2.0/authorize
spring.security.oauth2.client.provider.microsoft.token-uri=https://login.microsoftonline.com/<tenant>/oauth2/v2.0/token
spring.security.oauth2.client.provider.microsoft.jwk-set-uri=https://login.microsoftonline.com/<tenant>/discovery/v2.0/keys
spring.security.oauth2.client.provider.microsoft.user-info-uri=https://graph.microsoft.com/oidc/userinfo
spring.security.oauth2.client.provider.microsoft.user-name-attribute=sub
```

Microsoft is **not** a built‑in known provider under the name "microsoft", so we
describe it fully with two blocks:

- **registration.microsoft.\*** — our app's identity and what we request:
  - `provider=microsoft` — links this registration to the `provider.microsoft`
    block below.
  - `authorization-grant-type=authorization_code` — the standard, most secure
    OAuth2 flow (get a one‑time code, exchange it server‑side for tokens).
  - `redirect-uri` — again the shared `/app-auth`.
- **provider.microsoft.\*** — the actual Microsoft/Azure endpoints:
  - `authorization-uri` — where the user is sent to sign in.
  - `token-uri` — where our server exchanges the code for tokens.
  - `jwk-set-uri` — the public keys used to verify Microsoft's ID token signature.
  - `user-info-uri` — Microsoft Graph endpoint that returns the profile (name,
    email, sub).
  - `user-name-attribute=sub` — which field uniquely names the user (`sub` = the
    stable subject id).
  - The `<tenant>` is the Azure directory (tenant) id from the original project.

### JWT

```properties
jwt.secret.key=1ac2f4e78320151e7b53963f1f5ddbd0f6e7c0a8304d29c61aabbec31b94b840
jwt.token.validity=1800000
```

- `jwt.secret.key` — the secret used to **sign** and **verify** our JWTs. Anyone
  who knows this key can forge tokens, so in production it must be kept secret and
  loaded from an environment variable, not committed to source.
- `jwt.token.validity=1800000` — token lifetime in **milliseconds** = 30 minutes.
  After this, the token expires and the user must log in again.

These two values are read by `JwtService` (see file 5).
