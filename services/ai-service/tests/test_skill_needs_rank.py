import pytest

from app.ai_v2.needs import build_needs
from app.ai_v2.rank import rank_candidates


def test_needs_are_bounded_and_have_capability_assessment():
    result = build_needs(
        [
            {"id": "f1", "field": "features", "value": ["결제"]},
            {"id": "f2", "field": "external_integrations", "value": ["Stripe"]},
        ]
    )
    assert len(result["needs"]) <= 6
    assert len(result["claims"]) == 9
    assert {claim["need"] for claim in result["claims"]} <= {
        "REQUIRED", "OPTIONAL", "UNDETERMINED", "NOT_REQUESTED"}


def test_capability_claims_consider_all_confirmed_features_after_six_need_limit():
    names = [
        "프로젝트 문서 참조", "코드 저장소 탐색", "코드 리뷰", "테스트 작성",
        "테스트 실행", "브라우저 검증", "API 검증", "데이터베이스 스키마 점검",
        "AI 모델 평가",
    ]
    facts = [{"id": f"profile.item.features.{index}", "field": "features", "value": name}
             for index, name in enumerate(names)]

    result = build_needs(facts)

    assert len(result["needs"]) == 6
    assert {claim["capabilityKey"] for claim in result["claims"] if claim["need"] == "REQUIRED"} == {
        "cap_document_reference", "cap_repository_navigation", "cap_code_review",
        "cap_test_authoring", "cap_test_execution", "cap_browser_verification",
        "cap_api_verification", "cap_database_schema_inspection", "cap_ai_evaluation",
    }
    assert result["questions"] == []


def test_unmentioned_capabilities_do_not_block_explicit_browser_recommendation():
    result = build_needs([
        {"id": "profile.item.features.0", "field": "features", "value": "브라우저 검증"},
    ])
    claims = {claim["capabilityKey"]: claim for claim in result["claims"]}
    assert claims["cap_browser_verification"]["need"] == "REQUIRED"
    assert all(claim["need"] == "NOT_REQUESTED" for key, claim in claims.items()
               if key != "cap_browser_verification")
    assert result["questions"] == []


def test_uncertain_capability_generates_only_its_source_question():
    result = build_needs([
        {"id": "profile.item.features.0", "field": "features", "value": "브라우저 검증 여부 미정"},
        {"id": "environment.clientVersion", "field": "context", "value": "2.1.270"},
    ])
    claims = {claim["capabilityKey"]: claim for claim in result["claims"]}
    assert claims["cap_browser_verification"]["need"] == "UNDETERMINED"
    assert result["questions"] == [{"field": "profile.item.features.0",
                                    "reason": "브라우저 검증 여부를 확인해 주세요."}]


def test_runtime_context_and_unrelated_words_do_not_create_skill_needs():
    result = build_needs([
        {"id": "developer.version", "field": "context", "value": "1"},
        {"id": "profile.item.features.0", "field": "features", "value": "상품 리뷰와 email 알림"},
    ])
    assert all(claim["need"] == "NOT_REQUESTED" for claim in result["claims"])
    assert result["questions"] == []


def test_rank_returns_only_candidate_ids_and_scores_between_zero_and_hundred():
    result = rank_candidates(
        needs=[{"id": "n1", "name": "결제", "query": "payment", "sourceFields": ["f1"]}],
        candidates=[
            {"id": "c1", "name": "payment-guide", "content": "payment integration instructions"},
            {"id": "c2", "name": "unrelated", "content": "css colors"},
        ],
    )
    assert all(match["candidateId"] in {"c1", "c2"} for match in result["matches"])
    assert all(0 <= match["score"] <= 100 for match in result["matches"])


def test_rank_enforces_limits():
    with pytest.raises(ValueError, match="INVALID_SKILL_REQUEST"):
        rank_candidates([], [{"id": "c", "name": "x", "content": "x"} for _ in range(16)])
