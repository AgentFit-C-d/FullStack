# AgentFit FullStack

AgentFit MVP monorepo baseline.

## Repository Layout

```text
apps/
  web/                 # Next.js frontend, to be added by frontend team
services/
  core-api/            # Spring Boot API for A/B domain, persistence, audit, and orchestration
  ai-service/          # FastAPI service for AI request handling and structured extraction
contracts/
  openapi/             # API contracts shared by frontend, core API, and AI service
```

## Backend Services

- `services/core-api`: Single-module Spring Boot project for A/B development. Only the application entry point and `/health` endpoint are implemented.
- `services/ai-service`: Independent FastAPI project for AI development. Only the application entry point and `/health` endpoint are implemented.
- `contracts/openapi`: Initial OpenAPI files describing the health endpoints, not the full business API contract.

## Setup Scope

The layout follows section 2 (pages 4-5) of the development baseline in `Docs/`.
A/B features belong in the same Spring Boot application; FastAPI remains a separate service.
The planned flow is Next.js -> Spring Boot -> FastAPI -> external LLM, with PostgreSQL access owned by Spring Boot.
Database access, authentication, business features, service-to-service HTTP calls, and external LLM integration are not implemented yet.
The frontend directory is a placeholder.

Current scaffold versions are Java 21 / Spring Boot 3.3.5 and Python 3.11+ / FastAPI.
These versions were selected for the scaffold and are not prescribed by section 2 of the baseline.
Maven is required for the commands below; a Maven wrapper is not included yet.

## Local Run

Prerequisites: JDK 21, Maven, and Python 3.11+. Each service manages its own dependencies.
Build output (`target/`), Python virtual environments (`.venv/`), IDE settings, and local `.env` files are intentionally excluded from Git.
Maven downloads Java dependencies during the first build; the Python install command below installs the AI service dependencies.

```powershell
cd services/core-api
mvn spring-boot:run
```

```powershell
cd services/ai-service
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -e ".[dev]"
uvicorn app.main:app --reload --port 8001
```

Default ports:

- Core API: `8080`
- AI service: `8001`

## Local Verification

- Core API: Maven package build and one health endpoint test passed with Java 21.0.7. The packaged application returned HTTP 200 with `{"service":"core-api","status":"ok"}` from `/health`.
- AI service: Both health tests passed. Run `python -m pytest -q` from `services/ai-service` after installing the development dependencies.

These checks cover the scaffold only, not database, business, or AI integration.
