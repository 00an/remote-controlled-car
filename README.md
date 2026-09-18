# Remote Controlled Car — Full-Stack IoT Project

## Project structure

| Folder              | Description                                        |
| ------------------- | --------------------------------------------------- |
| `backend`           | Java / Spring Boot backend                          |
| `frontend`          | TypeScript / Next.js frontend                        |
| `hardware/firmware`  | ESP32 / C++ car firmware + the Python wheel-input script |
| `hardware`          | PCB notes, datasheets, bill of materials             |
| `ai`                | ETL pipeline + Streamlit analytics dashboard          |
| `docs`              | architecture notes and decision records               |

## Architecture

```
Wheel (hardware)
   │  pygame reads axes / buttons
   ▼
steering-input (Python)
   │  POST /v1/api/controller  (HTTP)
   ▼
backend (Spring Boot)
   │  broadcast over WebSocket /ws
   ├──────────────┬──────────────┐
   ▼              ▼              ▼
Dashboard     ESP32 car       MongoDB
(Next.js)     (motors)        (input logs)

PostgreSQL ← users + rides (Spring Data JPA)
```

The backend is the hub. The Python script reads the wheel and POSTs the input to the backend. The backend immediately forwards that input over a WebSocket to every connected client — both the dashboard (for live visualization) and the ESP32 on the car (which drives the motors).

## Setup

### Requirements

- Java 21
- Node.js 20+
- Python 3.11+

### Backend

```bash
cd backend
sh mvnw spring-boot:run
```

Runs on `http://localhost:3000`. The `dev` profile is active by default and uses an H2 in-memory database, so no extra setup is needed.

The H2 console is available at [`http://localhost:3000/h2-console`](http://localhost:3000/h2-console). Fill in the following:

```
JDBC URL: jdbc:h2:mem:devdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=true
User:     sa
Password: leave empty
```

For production (PostgreSQL), activate the `prod` profile via `SPRING_PROFILES_ACTIVE=prod`.

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Runs on `http://localhost:8080`. Create a `.env` file in `frontend/` with:

```
NEXT_PUBLIC_API_URL=http://localhost:3000
```

### Steering input (Python)

```bash
cd hardware/firmware/steering-input
pip install -r requirements.txt
python main.py
```

Requires a connected controller (e.g. a Logitech G29). Create a `.env` file in `hardware/firmware/steering-input/` with:

```
BACKEND_URL=http://localhost:3000/v1/api/controller
POLL_HZ=60
```

### ESP32 firmware

Opened with PlatformIO. Configure Wi-Fi + backend URL in `hardware/firmware/esp32-remote-car/include/config.h`, then flash via PlatformIO.

## Tech stack

**Backend**
- Spring Boot 3.5 (Java 21)
- Spring Data JPA (Hibernate) + PostgreSQL for users and rides
- Spring Data MongoDB for controller-input logs
- Flyway for schema migrations
- Spring Security with JWT (HttpOnly cookies)
- WebSocket (`spring-boot-starter-websocket`)
- springdoc-openapi for Swagger UI

**Frontend**
- Next.js 16 (App Router) + React 19
- TypeScript
- Tailwind CSS
- next-intl for i18n (NL / EN)

**Other**
- pygame for wheel input (Python)
- PlatformIO + Arduino framework (ESP32, C++)
- JaCoCo for test coverage
- axe-core for WCAG accessibility checks

## Key decisions

- **JWT in an HttpOnly cookie** instead of localStorage. An HttpOnly cookie can't be read by JavaScript, which limits XSS risk. The server sets and clears the cookie itself.
- **WebSockets instead of polling** for the live input. The wheel sends input at ~60 Hz; polling would create unnecessary requests and latency. With a WebSocket the connection stays open and every update is pushed directly to both the dashboard and the car.
- **API versioning via the `/v1` prefix** — applied to all controllers through `WebConfig`, so parallel versions can run later without breaking existing clients.
- **Schema migrations with Flyway** — every schema change is a version-numbered SQL file in `src/main/resources/db/migration/` and runs automatically on startup. `hibernate.ddl-auto=none` prevents Hibernate from touching the schema itself.
- **H2 in-memory for local dev and tests**, Postgres in production. H2 runs in PostgreSQL-compatibility mode so Flyway can use the same SQL.
- **MongoDB for controller-input logs** — that data arrives at high volume and has no relational structure. NoSQL is more practical here than rows in Postgres.
- **i18n locale in the URL** (`/nl/`, `/en/`) — handled by next-intl middleware. Gives shareable, indexable URLs per language.

## Production

The application runs in production on an **OKD (OpenShift)** cluster at `apps.okd.ucll.cloud`. Backend and frontend run as separate deployments, with PostgreSQL as the database in the same cluster.

- **Backend**: `backend-itip-nl-14.apps.okd.ucll.cloud`
- **Frontend**: `frontend-itip-nl-14.apps.okd.ucll.cloud`

### Container images

Both backend and frontend have a `Dockerfile` in their root folder. These are built in the OKD cluster itself (BuildConfig) and rolled out as Deployments.

### Routes (TLS)

Public endpoints are configured via OpenShift Route resources with TLS, defined in:

- [`backend/okd/route-esp32-tls.yaml`](backend/okd/route-esp32-tls.yaml)
- [`frontend/okd/route-frontend-tls.yaml`](frontend/okd/route-frontend-tls.yaml)

### Differences from local dev

| Aspect           | Local (dev)             | Production (OKD)        |
| ---------------- | ----------------------- | ----------------------- |
| Database         | H2 in-memory            | PostgreSQL              |
| Spring profile   | `dev` (default)         | `prod` (via env var)    |
| Cache            | off                     | Redis                   |
| TLS              | no                      | yes, via OpenShift Route |
| Cookie domain    | localhost               | `.apps.okd.ucll.cloud`  |

## Testing

### Backend

```bash
cd backend
sh mvnw test
```

Generates a JaCoCo coverage report at `target/site/jacoco/index.html`.

### Frontend accessibility

```bash
cd frontend
npm run a11y
```

Automatically checks all pages against WCAG 2.0 A/AA and 2.1 A/AA via axe-core. Pages are auto-discovered based on the `app/[locale]/` directory.

## Further documentation

- [`backend/patterns-documentation.md`](backend/patterns-documentation.md) — design patterns used in the backend
- [`backend/refactor-documentation.md`](backend/refactor-documentation.md) — refactor of the cookie logic in UserController (before/after)
- [`SECURITY.md`](SECURITY.md) — security policy and approach
- Swagger UI: `http://localhost:3000/swagger-ui/index.html` (local)
- OpenAPI spec: `http://localhost:3000/v1/api-docs`
