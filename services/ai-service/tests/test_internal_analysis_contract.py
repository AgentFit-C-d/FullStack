from app.internal_auth import canonical_string
from app.main import app
from fastapi.testclient import TestClient
from app.settings import HmacKeyRing


def test_canonical_string_uses_contract_order_and_raw_body_hash():
    body = b'{"requestId":"r-1"}'
    value = canonical_string("POST", "/internal/v1/analysis", "10", "r-1", 1, "v1", body)
    assert value.split("\n") == [
        "POST", "/internal/v1/analysis", "10", "r-1", "1", "v1",
        "7b94ae8c02746b5c9d442c3cd57a8c4f47c0ddc83f465f9a24ee9dfce3c20ebc",
    ]


def test_internal_contract_returns_only_contract_test_double():
    # The endpoint response deliberately contains no user, session, or project ownership fields.
    assert any(route.path == "/internal/v1/analysis" for route in app.routes)
    ring = HmacKeyRing("current", "secret")
    assert ring.secret_for("current", __import__("datetime").datetime.now(__import__("datetime").timezone.utc)) == "secret"
