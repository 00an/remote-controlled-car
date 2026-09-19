# Backend

Spring Boot API and WebSocket hub for the remote-controlled car. Receives
steering input over HTTP, re-broadcasts it in real time over WebSocket to
the ESP32 and the dashboard, handles user accounts/auth, and logs every
control frame.

## STACK

- Spring Boot 3.5 (Java 21)
- Spring Data JPA (Hibernate) + PostgreSQL — users, rides
- Spring Data MongoDB — control-input logs
- Flyway — schema migrations
- Spring Security with JWT in an HttpOnly cookie
- Spring WebSocket — `/ws/controller`, `/ws/camera`
- springdoc-openapi — Swagger UI / OpenAPI spec
- Redis — caching (prod only)

## SETUP COMMANDS

```
mvnw.cmd spring-boot:run
```

Runs on `http://localhost:3000` with the `dev` profile (H2 in-memory
database, PostgreSQL-compatibility mode — no local Postgres/Mongo needed).

Run the tests:

```
mvnw.cmd test
```

Coverage report: `target/site/jacoco/index.html`.

A test account is seeded automatically on startup (`dev` profile only):

| Username | Password | Email |
|---|---|---|
| `demo` | `password123` | `demo@example.com` |

It comes with 3 sample rides already logged. Seeding happens in
`DbInitializer` and re-runs (wiping and re-seeding) on every restart.

## STRUCTURE

```
src/main/java/be/ucll/itintegrationproject/NL_14_backend/
├── config/       security, CORS, WebSocket, JWT, OpenAPI configuration
├── controller/   REST controllers, WebSocket handlers, request/response DTOs
├── exception/    custom exceptions + global exception handler
├── model/        JPA entities (User, Ride) and MongoDB documents
├── repository/   Spring Data repositories (JPA + Mongo)
└── service/      business logic, WebSocket hubs, logging services
```

Migrations live in `src/main/resources/db/migration/` (Flyway).

## ENV VARIABLES

Local dev needs none — the `dev` profile (default) uses H2 in-memory and
has sensible defaults for everything else.

Production (`SPRING_PROFILES_ACTIVE=prod`) reads all secrets from the
environment, nothing is hardcoded:

| Variable | Purpose |
|---|---|
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | PostgreSQL credentials |
| `SPRING_DATA_MONGODB_URI` | MongoDB connection string |
| `SPRING_DATA_MONGODB_DATABASE` | Mongo database name (defaults to `nl14`) |
| `JWT_SECRET_KEY` | signing key for auth JWTs |
| `JWT_COOKIE_DOMAIN` | cookie domain for the auth cookie |
| `CORS_ALLOWED_ORIGINS` | comma-separated allow-list for CORS + WebSocket origins |
| `REDIS_HOST` / `REDIS_PORT` | Redis host/port for the cache layer (defaults to `redis-primary` / `6379`, the in-cluster service) |

## NOTES

- Local dev uses H2 in-memory (PostgreSQL-compatibility mode) and a simple
  in-process cache — no Postgres, Mongo or Redis needed to run it. The H2
  console is at `http://localhost:3000/h2-console` (JDBC URL
  `jdbc:h2:mem:devdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=true`,
  user `sa`, empty password). Production switches to Postgres/Mongo/Redis
  automatically via the `prod` profile.
- API is versioned under `/v1` (see `WebConfig`).
- Auth is a JWT in an HttpOnly cookie, resolved by a custom
  `CookieBearerTokenResolver` — not a bearer header.
- `/v1/api/controller/**` is intentionally public: the physical steering
  script and the ESP32 have no login flow. See the comment above that
  matcher in `SecurityConfig` for the full rationale.
- Further reading: [`patterns-documentation.md`](patterns-documentation.md)
  (design patterns used) and [`refactor-documentation.md`](refactor-documentation.md)
  (a before/after refactor of the cookie-handling logic).
