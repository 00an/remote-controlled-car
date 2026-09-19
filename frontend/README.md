# Frontend

Next.js dashboard for the remote-controlled car. Shows the live WebSocket
control stream and camera feed, and lets a driver steer with the keyboard
as a fallback to the physical wheel.

## STACK

- Next.js 16 (App Router) + React 19, TypeScript
- Tailwind CSS 4
- next-intl for i18n (`en` / `nl`, locale in the URL)
- husky + lint-staged for pre-commit formatting/linting
- axe-core for automated WCAG accessibility checks

## SETUP COMMANDS

```
echo NEXT_PUBLIC_API_URL=http://localhost:3000> .env && npm install && npm run dev
```

Runs on `http://localhost:8080`. (See [`.env.example`](.env.example) for
the full set of variables — `NEXT_PUBLIC_API_URL` should point at the
backend, `http://localhost:3000` for local dev.)

Other scripts:

```bash
npm run build       # production build
npm run lint         # ESLint
npm run format       # Prettier — write
npm run a11y         # axe-core accessibility check across all app/[locale] pages
```

## STRUCTURE

```
app/[locale]/    routed pages (home, login, signup, dashboard, rides, privacy)
components/      shared UI components (forms, header, privacy notice)
context/         React context (auth)
services/        typed API clients (users, rides)
lib/             fetch wrapper / API helpers
i18n/            next-intl request config
public/locale/   en/nl translation JSON
```

## ENV VARIABLES

| Variable              | Purpose                               |
| --------------------- | ------------------------------------- |
| `NEXT_PUBLIC_API_URL` | base URL of the backend API/WebSocket |

## NOTES

- Auth uses an HttpOnly cookie set by the backend — no token handling on
  the client.
- Security headers and a strict CSP are configured in `next.config.ts`.
- `middleware.ts` handles the `en`/`nl` locale routing.
