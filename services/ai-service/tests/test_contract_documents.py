from pathlib import Path


def test_shared_contract_documents_keep_all_internal_routes_and_error_envelope():
    root = Path(__file__).parents[3]
    contract = (root / "contracts" / "openapi" / "ai-service.yml").read_text(encoding="utf-8")
    assert "/internal/v1/analysis:" in contract
    assert "/internal/v1/skills/needs:" in contract
    assert "/internal/v1/skills/rank:" in contract
    assert "X-AgentFit-Signature" in contract
    assert "InternalErrorResponse" in contract
    assert "requestId" in contract


def test_core_public_contract_requires_confirmation_conflict():
    root = Path(__file__).parents[3]
    contract = (root / "contracts" / "openapi" / "core-api.yml").read_text(encoding="utf-8")
    assert "PROFILE_NOT_CONFIRMED" in contract
    assert '"409"' in contract
