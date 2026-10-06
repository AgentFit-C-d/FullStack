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

- `services/core-api`: Single-module Spring Boot project for A/B development. The application entry point and `/health` endpoint exist; B has also implemented pure Catalog, recommendation, selection, and Preview domain code. Authenticated business APIs and persistence are not connected.
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

- Core API: run `mvn -B -ntp clean verify` from `services/core-api`. The 2026-10-06 local run found 80 tests: 79 passed and one Windows symlink test was skipped. That run used JDK 24 to build Java 21 bytecode; the GitHub workflow uses JDK 21 on Linux.
- AI service: run `python -m pytest -q` from `services/ai-service` after installing the development dependencies. Both tests passed locally on Python 3.13; the GitHub workflow uses Python 3.11.

These checks do not verify database, authenticated API, real Catalog, external AI, or deployed-environment integration.

## Continuous Integration

The [GitHub Actions CI workflow](.github/workflows/ci.yml) runs on pushes, pull requests, and manual dispatch. It independently builds and tests `core-api` with Java 21 and `ai-service` with Python 3.11. A successful Core API job retains its tested JAR for seven days as a downloadable workflow artifact.

Deployment automation is not configured: the team has not chosen a deployment target, credentials, or environment yet. A downloadable artifact is a build result, not a deployment. Once those choices are agreed, the deploy workflow and post-deployment checks can be added under the A-owned deployment boundary.
