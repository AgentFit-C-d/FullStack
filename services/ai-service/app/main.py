import os
import logging
import hashlib
import json

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.health import health_payload
from app.internal_analysis import authenticate_analysis_request
from app.internal_auth import InternalAuthError, ReplayGuard
from app.settings import HmacKeyRing, SettingsError

app = FastAPI(title="AgentFit AI Service", version="0.1.0")
logger = logging.getLogger(__name__)
key_ring: HmacKeyRing | None = None
replay_guard = ReplayGuard()
analysis_service = None


@app.on_event("startup")
def validate_hmac_configuration() -> None:
    global key_ring, analysis_service
    key_ring = HmacKeyRing.from_env()
    try:
        from app.analysis.config import read_config
        from app.analysis.service import AnalysisService

        analysis_service = AnalysisService(read_config(os.environ))
    except (ImportError, ValueError):
        # Authentication remains available while provider configuration is incomplete.
        analysis_service = None


@app.get("/health")
def health() -> dict[str, str]:
    return health_payload()


@app.post("/internal/v1/analysis")
async def process_analysis(request: Request):
    try:
        if key_ring is None:
            raise SettingsError("HMAC configuration is not initialized")
        authenticated = await authenticate_analysis_request(request, key_ring, replay_guard)
    except (InternalAuthError, SettingsError) as exception:
        raw_body = await request.body()
        context = {}
        try:
            parsed_body = json.loads(raw_body)
            context = {
                "requestId": parsed_body.get("requestId", "unknown"),
                "generation": parsed_body.get("generation", "unknown"),
                "contractVersion": parsed_body.get("contractVersion", "unknown"),
            }
        except (TypeError, ValueError, json.JSONDecodeError):
            pass
        logger.warning(
            "Internal analysis authentication failed: %s method=%s path=%s keyId=%s timestamp=%s requestId=%s generation=%s contractVersion=%s bodySha256=%s",
            type(exception).__name__,
            request.method,
            request.url.path,
            request.headers.get("X-AgentFit-Key-Id", "missing"),
            request.headers.get("X-AgentFit-Timestamp", "missing"),
            context.get("requestId", "unknown"),
            context.get("generation", "unknown"),
            context.get("contractVersion", "unknown"),
            hashlib.sha256(raw_body).hexdigest(),
        )
        return JSONResponse(status_code=401, content={
            "error": {
                "code": "INTERNAL_AUTH_FAILED",
                "message": "Internal request authentication failed.",
                "requestId": "unknown",
            }
        })
    input_payload = authenticated.body.get("input")
    if not isinstance(input_payload, dict) or "inputRef" not in input_payload:
        return {
            "requestId": authenticated.request_id,
            "generation": authenticated.generation,
            "contractVersion": authenticated.contract_version,
            "result": {"kind": "CONTRACT_TEST_DOUBLE"},
        }
    if analysis_service is None:
        return JSONResponse(status_code=503, content={
            "error": {
                "code": "AI_SERVICE_NOT_CONFIGURED",
                "message": "AI provider configuration is unavailable.",
                "requestId": authenticated.request_id,
            }
        })
    try:
        result = await analysis_service.analyze(input_payload)
    except Exception as exception:
        code = getattr(exception, "code", "ANALYSIS_FAILED")
        logger.exception("AI analysis failed code=%s", code)
        return JSONResponse(status_code=503, content={
            "error": {
                "code": str(code),
                "message": "AI analysis failed.",
                "requestId": authenticated.request_id,
            }
        })
    return {
        "requestId": authenticated.request_id,
        "generation": authenticated.generation,
        "contractVersion": authenticated.contract_version,
        "result": result,
    }
