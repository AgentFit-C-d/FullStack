import base64
import hashlib
import hmac
import json
import time

from fastapi.testclient import TestClient

from app.main import app


def signed_headers(body: bytes, *, key_id: str = "test-current", secret: str = "test-secret", timestamp: int | None = None):
    timestamp = int(time.time()) if timestamp is None else timestamp
    payload = json.loads(body)
    canonical = "\n".join([
        "POST",
        "/internal/v1/analysis",
        str(timestamp),
        payload["requestId"],
        str(payload["generation"]),
        payload["contractVersion"],
        hashlib.sha256(body).hexdigest(),
    ])
    signature = base64.urlsafe_b64encode(
        hmac.new(secret.encode(), canonical.encode(), hashlib.sha256).digest()
    ).rstrip(b"=").decode()
    return {
        "X-AgentFit-Key-Id": key_id,
        "X-AgentFit-Timestamp": str(timestamp),
        "X-AgentFit-Signature": signature,
    }


def payload(request_id: str = "r-1") -> bytes:
    return json.dumps({
        "requestId": request_id,
        "generation": 1,
        "contractVersion": "v1",
        "input": {"fixture": "contract"},
    }, separators=(",", ":")).encode()


def test_valid_hmac_returns_contract_double():
    body = payload()
    with TestClient(app) as client:
        response = client.post("/internal/v1/analysis", content=body, headers=signed_headers(body))
    assert response.status_code == 200
    assert response.json()["result"]["kind"] == "CONTRACT_TEST_DOUBLE"


def test_body_tampering_and_expired_timestamp_are_indistinguishable_auth_failures():
    body = payload("r-tamper")
    headers = signed_headers(body)
    with TestClient(app) as client:
        tampered = client.post("/internal/v1/analysis", content=payload("different"), headers=headers)
        expired = client.post("/internal/v1/analysis", content=body, headers=signed_headers(body, timestamp=int(time.time()) - 301))
    assert tampered.status_code == expired.status_code == 401
    assert tampered.json()["error"]["code"] == expired.json()["error"]["code"] == "INTERNAL_AUTH_FAILED"


def test_same_request_id_and_body_is_rejected_for_ten_minutes():
    body = payload("r-duplicate")
    with TestClient(app) as client:
        first = client.post("/internal/v1/analysis", content=body, headers=signed_headers(body))
        second = client.post("/internal/v1/analysis", content=body, headers=signed_headers(body))
    assert first.status_code == 200
    assert second.status_code == 401


def test_unknown_key_id_is_rejected_without_access_to_user_or_project_data():
    body = payload("r-unknown")
    with TestClient(app) as client:
        response = client.post("/internal/v1/analysis", content=body,
            headers=signed_headers(body, key_id="unknown", secret="test-secret"))
    assert response.status_code == 401
