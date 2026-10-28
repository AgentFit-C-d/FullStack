# AgentFit AI Service

FastAPI service for AgentFit AI workflows.

## Responsibilities

- Receive only HMAC-verified `POST /internal/v1/analysis` requests from `core-api`.
- For a real `inputRef` request, fetch the leased planning input from Core, run the configured provider pipeline, and return the validated analysis contract. Fixture requests without `inputRef` still receive the contract test-double response for compatibility tests.
- Keep provider-specific details out of the core domain service.
- Do not access the Core API PostgreSQL database, browser session, or user/project ownership data.

## HMAC configuration

Both services require `AGENTFIT_HMAC_CURRENT_KEY_ID` and `AGENTFIT_HMAC_CURRENT_SECRET` together. Optional rollover configuration must provide all three of `AGENTFIT_HMAC_PREVIOUS_KEY_ID`, `AGENTFIT_HMAC_PREVIOUS_SECRET`, and `AGENTFIT_HMAC_PREVIOUS_VALID_UNTIL`; an expired previous key is rejected. Do not place secrets, signatures, or raw request bodies in logs, Audit, or databases.

## Analysis configuration

Set `AI_SERVICE_TOKEN` to the same value as Core's `AGENTFIT_AI_SERVICE_TOKEN`, and set `UPSTAGE_API_KEY` to enable the default Upstage provider. `API_INTERNAL_URL` defaults to `http://127.0.0.1:8080`; override it when Core runs elsewhere.

## Run

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -e ".[dev]"
uvicorn app.main:app --reload --port 8001
```

## Test

```powershell
pytest
```
