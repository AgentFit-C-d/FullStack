# AgentFit AI Service

FastAPI service for AgentFit AI workflows.

## Responsibilities

- Receive AI requests from `core-api`.
- Normalize AI provider responses into structured JSON.
- Keep provider-specific details out of the core domain service.

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
