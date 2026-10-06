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

- `services/core-api`: Spring Boot 3 service. Owns project/profile/recommendation/configuration data and calls the AI service over HTTP.
- `services/ai-service`: FastAPI service. Receives AI requests from `core-api` and returns structured JSON.
- `contracts/openapi`: OpenAPI files for service boundaries.

## Local Run

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
