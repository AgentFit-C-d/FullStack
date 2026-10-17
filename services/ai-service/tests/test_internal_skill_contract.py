import hashlib
import hmac
import json
import time
from datetime import datetime, timedelta, timezone

from fastapi.testclient import TestClient

from app.main import app


def signed_headers(body: bytes, path: str):
    payload = json.loads(body)
    timestamp = str(int(time.time()))
    canonical = "\n".join([
        "POST", path, timestamp, payload["requestId"], str(payload["generation"]),
        payload["contractVersion"], hashlib.sha256(body).hexdigest(),
    ])
    signature = hmac.new(b"test-secret", canonical.encode(), hashlib.sha256).digest()
    import base64
    return {
        "X-AgentFit-Key-Id": "test-current",
        "X-AgentFit-Timestamp": timestamp,
        "X-AgentFit-Signature": base64.urlsafe_b64encode(signature).rstrip(b"=").decode(),
    }


def body(request_id: str, input_value: dict) -> bytes:
    return json.dumps({
        "requestId": request_id,
        "generation": 1,
        "contractVersion": "v1",
        "input": input_value,
    }, separators=(",", ":")).encode()


def test_needs_and_rank_share_hmac_context_and_return_structured_results():
    deadline = (datetime.now(timezone.utc) + timedelta(seconds=30)).isoformat().replace("+00:00", "Z")
    needs_body = body("needs-1", {"facts": [{"id": "f1", "field": "features", "value": ["결제"]}], "deadline": deadline})
    with TestClient(app) as client:
        needs = client.post("/internal/v1/skills/needs", content=needs_body, headers=signed_headers(needs_body, "/internal/v1/skills/needs"))
        rank_body = body("rank-1", {"needs": needs.json()["result"]["needs"], "candidates": [{"id": "c1", "name": "payment", "content": "payment integration"}]})
        rank = client.post("/internal/v1/skills/rank", content=rank_body, headers=signed_headers(rank_body, "/internal/v1/skills/rank"))
    assert needs.status_code == 200
    assert len(needs.json()["result"]["claims"]) == 9
    assert rank.status_code == 200
