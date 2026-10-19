# Ephemeris

## Stack

- **Backend:** Spring Boot, JDBC, Postgres
- **Frontend:** Vite + React
- **Ops:** Docker Compose, nginx, `opstools/`

## Local

```bash
# backend
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# frontend
cd frontend && npm install && npm run dev
```