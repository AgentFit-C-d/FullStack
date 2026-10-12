from app.internal_auth import canonical_string


def test_canonical_string_uses_contract_order_and_raw_body_hash():
    body = b'{"requestId":"r-1"}'
    value = canonical_string("POST", "/internal/v1/analysis", "10", "r-1", 1, "v1", body)
    assert value.split("\n") == [
        "POST", "/internal/v1/analysis", "10", "r-1", "1", "v1",
        "7b94ae8c02746b5c9d442c3cd57a8c4f47c0ddc83f465f9a24ee9dfce3c20ebc",
    ]
