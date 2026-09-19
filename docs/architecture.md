# Architecture

## Overview

A full-stack IoT system for driving a remote-controlled car in real time. A steering input (a wheel via `pygame`, or keyboard from the web dashboard) is sent to a Spring Boot backend, which broadcasts it over a WebSocket to every connected client at once: the ESP32 on the car (which drives the motors) and a Next.js dashboard (for live visualization). Every input frame is also logged to MongoDB for later analysis, while user accounts and ride metadata live in PostgreSQL.

## Stack

**Frontend:** Next.js 16 (App Router), React 19, TypeScript, Tailwind CSS, next-intl for i18n (NL/EN)

**Backend:** Spring Boot 3.5 (Java 21), Spring Data JPA + PostgreSQL (users, rides), Spring Data MongoDB (controller-input logs), Flyway migrations, Spring Security with JWT in HttpOnly cookies, WebSocket, springdoc-openapi

**Data / AI:** Python ETL pipeline + Streamlit dashboard, reading directly from MongoDB for analytics on driving sessions

**Hardware:** ESP32 (C++, PlatformIO/Arduino framework) for the car firmware; a Python (`pygame`) script for wheel input

**Deployment:** Docker images for backend and frontend, deployed to a Kubernetes (OKD/OpenShift) cluster

## Repo structure

```
frontend/     web dashboard
backend/      API, WebSocket hub, auth
hardware/     firmware, PCB files, datasheets, BOM
docs/         architecture notes, setup guides, decision records
ai/           ETL pipeline + analytics dashboard
```

## Components

**Frontend:** Live dashboard showing the WebSocket stream, plus keyboard-based steering as a fallback input source.

**Backend:** The hub of the system. Receives steering input over HTTP, re-broadcasts it over WebSocket to all connected clients, persists users/rides to PostgreSQL via JPA, and logs every control frame to MongoDB.

**Hardware nodes:** ESP32 firmware that connects over WebSocket, receives steering commands, and drives the car's motors and an onboard OLED display.

**Data layer:** PostgreSQL for relational data (users, rides) via Spring Data JPA with Flyway migrations; MongoDB for high-volume, schema-flexible control-input logs.

**Data / AI:** A separate ETL pipeline reads the raw and filtered MongoDB collections, computes derived features (smoothness, latency, session summaries), and exposes them through an interactive Streamlit dashboard. See [`ai/README.md`](../ai/README.md) for the full breakdown.

## Data flow

```
Steering wheel / keyboard
   |  pygame reads axes/buttons, or dashboard sends keypresses
   v
steering-input (Python) or frontend
   |  POST /v1/api/controller
   v
Backend (Spring Boot)
   |  broadcasts over WebSocket
   +------------------+------------------+
   v                  v                  v
Dashboard         ESP32 (car)        MongoDB
(live view)       (drives motors)    (raw + filtered logs)

PostgreSQL <- users + rides (Spring Data JPA)
```

## Decisions

See [docs/decisions/](decisions).
