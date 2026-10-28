import os
import logging

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.health import health_payload
from app.internal_analysis import authenticate_analysis_request
from app.internal_auth import InternalAuthError, ReplayGuard
from app.settings import HmacKeyRing, SettingsError
from app.ai_v2.analysis import AnalysisCore, InformationExtractProvider, SolarProvider, decode_document
from app.ai_v2.needs import build_needs
from app.ai_v2.rank import rank_candidates

app = FastAPI(title="AgentFit AI Service", version="0.1.0")
logger = logging.getLogger(__name__)
key_ring: HmacKeyRing | None = None
replay_guard = ReplayGuard()
analysis_service = None
provider_name = os.environ.get("AGENTFIT_AI_V2_PROVIDER", "deterministic")
if provider_name == "solar":
    provider = SolarProvider(os.environ.get("UPSTAGE_API_KEY", ""))
elif provider_name == "information-extract":
    provider = InformationExtractProvider(
        os.environ.get("INFORMATION_EXTRACT_API_KEY", os.environ.get("UPSTAGE_API_KEY", "")),
        os.environ.get("INFORMATION_EXTRACT_ENDPOINT", ""),
    )
else:
    provider = None
analysis_core = AnalysisCore(provider)


@app.on_event("startup")
def validate_hmac_configuration() -> None:
    global key_ring, analysis_service
    key_ring = HmacKeyRing.from_env()
    try:
        from app.analysis.config import read_config
        from app.analysis.service import AnalysisService

        analysis_service = AnalysisService(read_config(os.environ))
    except (ImportError, ValueError):
        analysis_service = None


@app.get("/health")
def health() -> dict[str, str]:
    return health_payload()


async def _authenticated(request: Request):
    try:
        if key_ring is None:
            raise SettingsError("HMAC configuration is not initialized")
        return await authenticate_analysis_request(request, key_ring, replay_guard)
    except (InternalAuthError, SettingsError):
        return JSONResponse(status_code=401, content={
            "error": {
                "code": "INTERNAL_AUTH_FAILED",
                "message": "Internal request authentication failed.",
                "requestId": "unknown",
            }
        })


def _context(authenticated, result):
    return {
        "requestId": authenticated.request_id,
        "generation": authenticated.generation,
        "contractVersion": authenticated.contract_version,
        "result": result,
    }


def _failure(request_id: str, code: str, status: int = 400):
    return JSONResponse(status_code=status, content={
        "error": {"code": code, "message": "AI internal request failed.", "requestId": request_id}
    })


@app.post("/internal/v1/analysis")
async def process_analysis(request: Request):
    authenticated = await _authenticated(request)
    if isinstance(authenticated, JSONResponse):
        return authenticated
    try:
        payload = authenticated.body
        input_payload = payload["input"]
        if not isinstance(input_payload, dict):
            raise ValueError("INVALID_ANALYSIS_REQUEST")
        if "inputRef" in input_payload:
            if analysis_service is None:
                return _failure(authenticated.request_id, "AI_SERVICE_NOT_CONFIGURED", 503)
            try:
                return _context(authenticated, await analysis_service.analyze(input_payload))
            except Exception as error:
                code = getattr(error, "code", "ANALYSIS_FAILED")
                logger.exception("AI analysis failed code=%s", code)
                return _failure(authenticated.request_id, str(code), 503)
        kind = input_payload.get("inputKind", input_payload.get("kind"))
        document_id = input_payload.get("documentId", authenticated.request_id)
        deadline = input_payload.get("deadline", payload.get("deadline"))
        content = input_payload.get("contentBase64")
        if content is None and isinstance(input_payload.get("content"), str):
            raw_content = input_payload["content"].encode("utf-8")
        else:
            raw_content = decode_document(content)
        result = analysis_core.analyze(document_id, kind, raw_content, deadline)
        return _context(authenticated, result)
    except (KeyError, TypeError, ValueError) as error:
        code = str(error) or "INVALID_ANALYSIS_REQUEST"
        transient = {"ANALYSIS_TIMEOUT", "PROVIDER_NOT_CONFIGURED", "PROVIDER_UNAVAILABLE"}
        return _failure(authenticated.request_id, code, 503 if code in transient else 400)


@app.post("/internal/v1/skills/needs")
async def process_needs(request: Request):
    authenticated = await _authenticated(request)
    if isinstance(authenticated, JSONResponse):
        return authenticated
    try:
        payload = authenticated.body.get("input", authenticated.body)
        result = build_needs(payload["facts"])
        return _context(authenticated, result)
    except (KeyError, TypeError, ValueError) as error:
        return _failure(authenticated.request_id, str(error) or "INVALID_SKILL_REQUEST")


@app.post("/internal/v1/skills/rank")
async def process_rank(request: Request):
    authenticated = await _authenticated(request)
    if isinstance(authenticated, JSONResponse):
        return authenticated
    try:
        payload = authenticated.body.get("input", authenticated.body)
        result = rank_candidates(payload["needs"], payload["candidates"])
        return _context(authenticated, result)
    except (KeyError, TypeError, ValueError) as error:
        return _failure(authenticated.request_id, str(error) or "INVALID_SKILL_REQUEST")
