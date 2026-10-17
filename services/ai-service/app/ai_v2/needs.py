from __future__ import annotations

from typing import Any

CAPABILITY_KEYS = (
    "cap_document_reference",
    "cap_repository_navigation",
    "cap_code_review",
    "cap_test_authoring",
    "cap_test_execution",
    "cap_browser_verification",
    "cap_api_verification",
    "cap_database_schema_inspection",
    "cap_ai_evaluation",
)


def build_needs(facts: list[dict[str, Any]]) -> dict[str, Any]:
    if not isinstance(facts, list) or len(facts) > 100:
        raise ValueError("INVALID_SKILL_REQUEST")
    needs: list[dict[str, Any]] = []
    for fact in facts:
        if not isinstance(fact, dict) or not isinstance(fact.get("id"), str) or not fact["id"]:
            raise ValueError("INVALID_SKILL_REQUEST")
        value = fact.get("value")
        if value in (None, [], ""):
            continue
        field = fact.get("field")
        if field not in {"features", "external_integrations", "backend", "ai", "database", "deployment"}:
            continue
        values = value if isinstance(value, list) else [value]
        for part in values:
            if len(needs) >= 6:
                break
            text = str(part).strip()
            if not text:
                continue
            needs.append({
                "id": f"n{len(needs) + 1}",
                "name": text[:100],
                "query": _query_for(field, text),
                "sourceFields": [fact["id"]],
            })
    claims = []
    for key in CAPABILITY_KEYS:
        related = [need["id"] for need in needs if _related(key, need["name"])]
        claims.append({
            "capabilityKey": key,
            "need": "REQUIRED" if related else "UNDETERMINED",
            "reason": "확인된 사실에서 필요한 작업입니다." if related else "판단할 수 있는 확인 사실이 부족합니다.",
            "sourceFields": [field for need in needs if need["id"] in related for field in need["sourceFields"]],
        })
    unresolved = {claim["capabilityKey"] for claim in claims if claim["need"] == "UNDETERMINED"}
    question_fields = {fact["id"] for fact in facts if fact.get("id")}
    questions = [
        {"field": fact["id"], "reason": "이 확정 사실이 해당 Capability에 필요한지 확인해 주세요."}
        for fact in facts
        if unresolved and fact.get("id") in question_fields
    ]
    return {"needs": needs, "claims": claims, "questions": questions}


def _query_for(field: str, value: str) -> str:
    words = {"features": "feature", "external_integrations": "integration", "backend": "api", "ai": "ai", "database": "database", "deployment": "deployment"}
    return f"{words[field]} {value}"[:160]


def _related(capability: str, name: str) -> bool:
    text = name.casefold()
    mapping = {
        "cap_document_reference": ("문서", "document"),
        "cap_repository_navigation": ("repo", "코드", "repository"),
        "cap_code_review": ("검토", "review"),
        "cap_test_authoring": ("테스트", "test"),
        "cap_test_execution": ("테스트", "test"),
        "cap_browser_verification": ("브라우저", "browser"),
        "cap_api_verification": ("api", "연동", "integration"),
        "cap_database_schema_inspection": ("db", "database", "데이터베이스"),
        "cap_ai_evaluation": ("ai", "모델", "model"),
    }
    return any(token in text for token in mapping.get(capability, ()))
