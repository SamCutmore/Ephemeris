# Ephemeris

Scaffold for a multiplayer orbital / SOI simulation stack.

Templated from a known-working Spring + React + Postgres layout. Domain-specific market code has been removed; **authentication**, **password hashing**, and the **solver / API** architecture remain.

## Stack

- **Backend:** Spring Boot (`/api?q=…` solvers), JDBC, H2 (dev) / Postgres (prod)
- **Frontend:** Vite + React
- **Ops:** Docker Compose, nginx example, `opstools/`

## Auth

| Query | Result |
|-------|--------|
| `register: user;pass;pass` | `OK` / `Not OK` |
| `login: user;pass` | `{ token, userId, username }` |
| `logout: <token>` | `OK` / `Not OK` |

Password credentials use the existing `PasswordManager` / `PasswordCredentialService` (salted hash in `users.better_password`). Sessions live in `sessions`.

## Local

```bash
# backend
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# frontend
cd frontend && npm install && npm run dev
```

## Next

Tree-of-conics ephemeris, SOI interest, and Destiny-style ship segments.
