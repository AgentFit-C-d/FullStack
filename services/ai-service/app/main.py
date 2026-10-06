from fastapi import FastAPI

from app.health import health_payload

app = FastAPI(title="AgentFit AI Service", version="0.1.0")


@app.get("/health")
def health() -> dict[str, str]:
    return health_payload()
