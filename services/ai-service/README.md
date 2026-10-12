# AgentFit AI Service

FastAPI service for AgentFit AI workflows.

## Responsibilities

- Receive only HMAC-verified `POST /internal/v1/analysis` requests from `core-api`.
- This feature returns only the contract test-double response `{ "result": { "kind": "CONTRACT_TEST_DOUBLE" } }`; real provider prompt/input/output semantics are a later feature.
- Keep provider-specific details out of the core domain service.
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
pytest
```
