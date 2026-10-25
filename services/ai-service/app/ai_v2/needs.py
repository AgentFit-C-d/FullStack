from __future__ import annotations

import re
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
    evidence: list[tuple[str, str, str]] = []
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
            text = str(part).strip()
            if not text:
                continue
            evidence.append((fact["id"], field, text))
            if len(needs) < 6:
                needs.append({
                    "id": f"n{len(needs) + 1}",
                    "name": text[:100],
                    "query": _query_for(field, text),
                    "sourceFields": [fact["id"]],
                })
    claims = []
    unresolved_fields: dict[str, str] = {}
    for key in CAPABILITY_KEYS:
        matching = [(field_id, text) for field_id, field, text in evidence
                    if _related(key, field, text)]
        related = list(dict.fromkeys(field_id for field_id, _ in matching))
        uncertain = [(field_id, text) for field_id, text in matching if _uncertain(text)]
        if uncertain:
            need = "UNDETERMINED"
            for field_id, _ in uncertain:
                unresolved_fields.setdefault(field_id, key)
        else:
            need = "REQUIRED" if related else "NOT_REQUESTED"
        claims.append({
            "capabilityKey": key,
            "need": need,
            "reason": {"REQUIRED": "확인된 작업에 필요한 기능입니다.",
                       "UNDETERMINED": "확정 입력에 이 작업의 필요 여부가 미정입니다.",
                       "NOT_REQUESTED": "확정 입력에서 이 작업이 요청되지 않았습니다."}[need],
            "sourceFields": related,
        })
    questions = [
        {"field": field_id, "reason": f"{_capability_label(key)} 여부를 확인해 주세요."}
        for field_id, key in unresolved_fields.items()
    ]
    return {"needs": needs, "claims": claims, "questions": questions}


def _query_for(field: str, value: str) -> str:
    words = {"features": "feature", "external_integrations": "integration", "backend": "api", "ai": "ai", "database": "database", "deployment": "deployment"}
    return f"{words[field]} {value}"[:160]


def _related(capability: str, field: str, name: str) -> bool:
    if capability == "cap_api_verification" and field == "external_integrations":
        return True
    patterns = {
        "cap_document_reference": (r"문서", r"\b(?:document|docs|documentation)\b"),
        "cap_repository_navigation": (r"저장소", r"코드.{0,8}탐색", r"\b(?:repo|repository)\b"),
        "cap_code_review": (r"코드\s*(?:리뷰|검토)", r"\bcode\s+review\b", r"\bpull\s+request\s+review\b"),
        "cap_test_authoring": (r"테스트\s*(?:작성|코드|케이스)", r"\b(?:write\s+tests?|test\s+authoring)\b"),
        "cap_test_execution": (r"테스트", r"\b(?:test|tests|pytest)\b"),
        "cap_browser_verification": (r"브라우저", r"\b(?:browser|e2e|playwright)\b"),
        "cap_api_verification": (r"연동", r"\b(?:api|integration)\b"),
        "cap_database_schema_inspection": (r"스키마", r"쿼리", r"\b(?:schema|migration|sql)\b"),
        "cap_ai_evaluation": (r"(?:ai|모델)\s*(?:모델\s*)?평가", r"벤치마크", r"\b(?:eval|evaluation|benchmark)\b"),
    }
    return any(re.search(pattern, name, flags=re.IGNORECASE) for pattern in patterns.get(capability, ()))


def _uncertain(text: str) -> bool:
    return bool(re.search(r"미정|불확실|검토\s*필요|여부\s*확인|\b(?:tbd|undecided)\b", text, re.IGNORECASE))


def _capability_label(key: str) -> str:
    return {
        "cap_document_reference": "문서 참조",
        "cap_repository_navigation": "저장소 탐색",
        "cap_code_review": "코드 검토",
        "cap_test_authoring": "테스트 작성",
        "cap_test_execution": "테스트 실행",
        "cap_browser_verification": "브라우저 검증",
        "cap_api_verification": "API 검증",
        "cap_database_schema_inspection": "DB 스키마 점검",
        "cap_ai_evaluation": "AI 평가",
    }[key]
