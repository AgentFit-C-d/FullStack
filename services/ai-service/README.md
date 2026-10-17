# AgentFit AI Service

FastAPI service for AgentFit AI workflows.

## Responsibilities

- Receive only HMAC-verified `POST /internal/v1/analysis`, `/internal/v1/skills/needs`, and `/internal/v1/skills/rank` requests from `core-api`.
- Run the stateless AI v2 calculation core: PDF/Markdown/text extraction, grounded structured analysis, bounded needs, and candidate scoring.
- Return only structured proposals. Core API owns authentication context, PostgreSQL, user confirmation, audit, recommendation status, and Catalog persistence.
- Keep provider-specific details out of the core domain service. Set `AGENTFIT_AI_V2_PROVIDER=solar` and `UPSTAGE_API_KEY` for Solar, or `AGENTFIT_AI_V2_PROVIDER=information-extract` with `INFORMATION_EXTRACT_ENDPOINT` and its API key; the default provider is deterministic for local tests.
- Raw documents and full extraction are held only for the request and are never returned or persisted by this service.
- Do not access the Core API PostgreSQL database, browser session, or user/project ownership data.

## HMAC configuration

Both services require `AGENTFIT_HMAC_CURRENT_KEY_ID` and `AGENTFIT_HMAC_CURRENT_SECRET` together. Optional rollover configuration must provide all three of `AGENTFIT_HMAC_PREVIOUS_KEY_ID`, `AGENTFIT_HMAC_PREVIOUS_SECRET`, and `AGENTFIT_HMAC_PREVIOUS_VALID_UNTIL`; an expired previous key is rejected. Do not place secrets, signatures, or raw request bodies in logs, Audit, or databases.

## Run

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -e ".[dev]"
uvicorn app.main:app --reload --port 8001
```

## Test

```powershell
uv run --project . --extra dev pytest
```
