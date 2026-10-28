# Local Development Runner Design

## Intent

Provide one Windows command, `npm run dev:all`, that loads the repository's
root `.env` and starts the Vite frontend, Spring Boot Core API, and FastAPI AI
service in separate terminal windows so each service's logs remain visible.

## Goals

- Reuse values already stored in the root `.env` instead of requiring manual
  environment-variable entry on every run.
- Map repository-specific database names (`AGENTFIT_DB_*`) to Spring Boot's
  `SPRING_DATASOURCE_*` names.
- Keep the current local ports: frontend `5173`, Core API `8080`, AI service
  `8001`.
- Fail early with a clear message when `.env` is missing or required values are
  absent.

## Non-goals

- Changing Docker Compose behavior.
- Committing real provider credentials or OAuth secrets.
- Replacing service-specific test/build commands.

## Approach

The root `package.json` will expose `dev:all` and invoke a PowerShell runner.
The runner parses simple `KEY=VALUE` lines from `.env` without printing
values, derives the Spring datasource variables, and launches three child
PowerShell windows. The AI process uses the existing checked-in virtualenv;
Spring uses the configured JDK 21 path when present; the frontend runs through
its existing `npm run dev` script.

## Configuration

The root `.env` remains ignored by Git. The runner requires the existing DB and
HMAC settings and uses a local-only `AGENTFIT_AI_SERVICE_TOKEN` from `.env` for
Core-to-AI authentication. `UPSTAGE_API_KEY` may be supplied by `.env` or
`.env.docker` when real provider calls are desired.
