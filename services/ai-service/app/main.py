from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.health import health_payload
from app.internal_analysis import authenticate_analysis_request
from app.internal_auth import InternalAuthError, ReplayGuard
from app.settings import HmacKeyRing, SettingsError

app = FastAPI(title="AgentFit AI Service", version="0.1.0")
key_ring: HmacKeyRing | None = None
replay_guard = ReplayGuard()


@app.on_event("startup")
def validate_hmac_configuration() -> None:
    global key_ring
    key_ring = HmacKeyRing.from_env()


@app.get("/health")
def health() -> dict[str, str]:
    return health_payload()


@app.post("/internal/v1/analysis")
async def process_analysis(request: Request):
    try:
        if key_ring is None:
            raise SettingsError("HMAC configuration is not initialized")
        authenticated = await authenticate_analysis_request(request, key_ring, replay_guard)
    except (InternalAuthError, SettingsError):
        return JSONResponse(status_code=401, content={
            "error": {
                "code": "INTERNAL_AUTH_FAILED",
                "message": "Internal request authentication failed.",
                "requestId": "unknown",
            }
        })
    return {
        "requestId": authenticated.request_id,
        "generation": authenticated.generation,
        "contractVersion": authenticated.contract_version,
        "result": {"kind": "CONTRACT_TEST_DOUBLE"},
    }
