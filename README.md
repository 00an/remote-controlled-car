# Remote Controlled Car — Full-Stack IoT Project

A full-stack IoT system for driving a remote-controlled car in real time: a steering input is sent to a Spring Boot backend, which broadcasts it over WebSocket to a live dashboard and the ESP32 on the car.

## Getting Started

**Prerequisites:** Java 21, Node.js 20+ (Python 3.11+ only needed for the optional physical steering wheel)

The backend and frontend run standalone with no extra hardware needed — the dashboard has a keyboard-based fallback for steering input.

1. **Backend** (from `backend/`):

   ```
   mvnw.cmd spring-boot:run
   ```

   Runs on `http://localhost:3000`. The `dev` profile is active by default and uses an H2 in-memory database — no extra setup needed.

   The H2 console is available at [`http://localhost:3000/h2-console`](http://localhost:3000/h2-console):

   ```
   JDBC URL: jdbc:h2:mem:devdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=true
   User:     sa
   Password: leave empty
   ```

2. **Frontend** (from `frontend/`):

   ```
   echo NEXT_PUBLIC_API_URL=http://localhost:3000> .env && npm install && npm run dev
   ```

   (Creates the git-ignored `.env` the frontend needs to reach the backend, then installs and starts it.) Runs on `http://localhost:8080`.

3. Open `http://localhost:8080` and log in — you're driving via the dashboard's keyboard controls, with the same live WebSocket stream a real car would receive.

### Optional: physical steering wheel

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

### Optional: ESP32 firmware (requires the physical car)

Opened with PlatformIO. Configure Wi-Fi + backend URL in `hardware/firmware/esp32-remote-car/include/config.h`, then flash via PlatformIO.

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

```
cd backend
mvnw.cmd test
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
- [`SECURITY.md`](SECURITY.md) — security policy and approach
- Swagger UI: `http://localhost:3000/swagger-ui/index.html` (local)
- OpenAPI spec: `http://localhost:3000/v1/api-docs`
