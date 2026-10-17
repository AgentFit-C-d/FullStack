import base64
import hashlib
import hmac
import json
import time
from datetime import datetime, timedelta, timezone

from fastapi.testclient import TestClient

from app.main import app


def signed(body: bytes, path: str, timestamp: int | None = None) -> dict[str, str]:
    payload = json.loads(body)
    timestamp = int(time.time()) if timestamp is None else timestamp
    canonical = "\n".join([
        "POST", path, str(timestamp), payload["requestId"], str(payload["generation"]),
        payload["contractVersion"], hashlib.sha256(body).hexdigest(),
    ])
    signature = base64.urlsafe_b64encode(
        hmac.new(b"test-secret", canonical.encode(), hashlib.sha256).digest()
    ).rstrip(b"=").decode()
    return {
        "X-AgentFit-Key-Id": "test-current",
        "X-AgentFit-Timestamp": str(timestamp),
        "X-AgentFit-Signature": signature,
    }


def request_body(request_id: str, input_value: dict) -> bytes:
    return json.dumps({
        "requestId": request_id, "generation": 1, "contractVersion": "v1", "input": input_value,
    }, separators=(",", ":")).encode()


def test_all_v2_routes_reject_tampering_and_stale_context_before_business_logic():
    deadline = (datetime.now(timezone.utc) + timedelta(seconds=30)).isoformat().replace("+00:00", "Z")
    cases = [
        ("/internal/v1/analysis", {"kind": "text", "content": "domain: test", "deadline": deadline}),
        ("/internal/v1/skills/needs", {"facts": []}),
        ("/internal/v1/skills/rank", {"needs": [], "candidates": []}),
    ]
    with TestClient(app) as client:
        for index, (path, payload) in enumerate(cases):
            body = request_body(f"security-{index}", payload)
            headers = signed(body, path)
            tampered = bytearray(body)
            tampered[-2] = ord(" ")
            response = client.post(path, content=bytes(tampered), headers=headers)
            assert response.status_code == 401
            stale_body = request_body(f"stale-{index}", payload)
            stale = client.post(path, content=stale_body, headers=signed(stale_body, path, int(time.time()) - 301))
            assert stale.status_code == 401
