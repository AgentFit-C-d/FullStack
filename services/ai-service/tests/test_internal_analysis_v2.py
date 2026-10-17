import json
import time
import base64
import hashlib
import hmac
from datetime import datetime, timedelta, timezone

from fastapi.testclient import TestClient

from app.main import app


def test_analysis_route_preserves_context_and_returns_structured_result():
    body = json.dumps({
        "requestId": "v2-contract-1",
        "generation": 1,
        "contractVersion": "v1",
        "input": {
            "kind": "text",
            "documentId": "doc-v2-contract-1",
            "content": "domain: education",
            "deadline": (datetime.now(timezone.utc) + timedelta(seconds=30)).isoformat().replace("+00:00", "Z"),
        },
    }, separators=(",", ":")).encode()
    timestamp = str(int(time.time()))
    payload = json.loads(body)
    canonical = "\n".join(["POST", "/internal/v1/analysis", timestamp, payload["requestId"], "1", "v1", hashlib.sha256(body).hexdigest()])
    signature = base64.urlsafe_b64encode(hmac.new(b"test-secret", canonical.encode(), hashlib.sha256).digest()).rstrip(b"=").decode()
    headers = {"X-AgentFit-Key-Id": "test-current", "X-AgentFit-Timestamp": timestamp, "X-AgentFit-Signature": signature}

    with TestClient(app) as client:
        response = client.post("/internal/v1/analysis", content=body, headers=headers)

    assert response.status_code == 200
    assert response.json()["requestId"] == "v2-contract-1"
    assert response.json()["result"]["profile"]["domain"] == "education"
