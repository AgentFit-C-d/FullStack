from app.analysis.extraction import ExtractedDocument, SourcePage
from app.analysis.grounding import validate_grounding


def test_splits_multiple_backend_technologies_into_individual_review_candidates():
    text = "백엔드는 Java와 Spring Boot로 구현한다."

    result = validate_grounding(
        {"items": [{"field": "backend", "value": ["Java", "Spring Boot"], "quote": text, "context": "explicit"}]},
        ExtractedDocument("doc-1", "text", text, (SourcePage(1, 0, len(text)),)),
        "upstage",
        "information-extract",
    )

    candidates = [candidate for candidate in result["candidates"] if candidate["field"] == "backend"]
    assert [candidate["proposedValue"] for candidate in candidates] == [["Java"], ["Spring Boot"]]
    assert len({candidate["id"] for candidate in candidates}) == 2
    assert result["profile"]["backend"] == ["Java", "Spring Boot"]


def test_keeps_only_the_first_matching_location_as_candidate_evidence():
    quote = "백엔드는 Spring Boot로 구현한다."
    text = f"{quote}\n회의록\n{quote}"

    result = validate_grounding(
        {"items": [{"field": "backend", "value": ["Spring Boot"], "quote": quote, "context": "explicit"}]},
        ExtractedDocument("doc-1", "text", text, (SourcePage(1, 0, len(text)),)),
        "upstage",
        "information-extract",
    )

    candidate = next(candidate for candidate in result["candidates"] if candidate["field"] == "backend")
    assert len(candidate["evidence"]) == 1
    assert candidate["evidence"][0]["start"] == 0


def test_discards_a_field_label_when_no_actual_backend_technology_is_extracted():
    text = "웹/앱, 백엔드, AI 매칭"

    result = validate_grounding(
        {"items": [{"field": "backend", "value": ["백엔드"], "quote": text, "context": "explicit"}]},
        ExtractedDocument("doc-1", "text", text, (SourcePage(1, 0, len(text)),)),
        "upstage",
        "information-extract",
    )

    assert [candidate for candidate in result["candidates"] if candidate["field"] == "backend"] == []
    assert result["profile"]["backend"] is None


def test_moves_an_ai_capability_to_features_but_keeps_the_named_ai_api():
    text = "ChatGPT API를 활용한 AI 매칭 기능을 제공한다."

    result = validate_grounding(
        {"items": [{"field": "ai", "value": ["ChatGPT API", "AI 매칭"], "quote": text, "context": "explicit"}]},
        ExtractedDocument("doc-1", "text", text, (SourcePage(1, 0, len(text)),)),
        "upstage",
        "information-extract",
    )

    ai_candidates = [candidate for candidate in result["candidates"] if candidate["field"] == "ai"]
    feature_candidates = [candidate for candidate in result["candidates"] if candidate["field"] == "features"]

    assert [candidate["proposedValue"] for candidate in ai_candidates] == [["ChatGPT API"]]
    assert [candidate["proposedValue"] for candidate in feature_candidates] == [["AI 매칭"]]


def test_discards_broad_feature_labels_but_keeps_a_specific_workflow():
    generic_quote = "커뮤니티 제공"
    specific_quote = "커뮤니티 게시글 관리"
    text = f"{generic_quote}\n{specific_quote}"

    result = validate_grounding(
        {
            "items": [
                {"field": "features", "value": [generic_quote], "quote": generic_quote, "context": "explicit"},
                {"field": "features", "value": [specific_quote], "quote": specific_quote, "context": "explicit"},
            ]
        },
        ExtractedDocument("doc-1", "text", text, (SourcePage(1, 0, len(text)),)),
        "upstage",
        "information-extract",
    )

    feature_candidates = [candidate for candidate in result["candidates"] if candidate["field"] == "features"]
    assert [candidate["proposedValue"] for candidate in feature_candidates] == [[specific_quote]]


def test_uses_the_matching_single_evidence_for_each_split_technology():
    java_quote = "백엔드는 Java로 구현한다."
    spring_quote = "백엔드 프레임워크는 Spring Boot를 사용한다."
    text = f"{java_quote}\n{spring_quote}"

    result = validate_grounding(
        {
            "items": [
                {"field": "backend", "value": ["Java"], "quote": java_quote, "context": "explicit"},
                {"field": "backend", "value": ["Spring Boot"], "quote": spring_quote, "context": "explicit"},
            ]
        },
        ExtractedDocument("doc-1", "text", text, (SourcePage(1, 0, len(text)),)),
        "upstage",
        "information-extract",
    )

    candidates = [candidate for candidate in result["candidates"] if candidate["field"] == "backend"]
    evidence_by_value = {candidate["proposedValue"][0]: candidate["evidence"] for candidate in candidates}
    assert evidence_by_value["Java"][0]["quote"] == java_quote
    assert evidence_by_value["Spring Boot"][0]["quote"] == spring_quote
    assert all(len(evidence) == 1 for evidence in evidence_by_value.values())


def test_moves_unnamed_ai_capabilities_to_features():
    quote = "문서 요약과 개인화 추천을 제공한다."

    result = validate_grounding(
        {"items": [{"field": "ai", "value": ["문서 요약", "개인화 추천"], "quote": quote, "context": "explicit"}]},
        ExtractedDocument("doc-1", "text", quote, (SourcePage(1, 0, len(quote)),)),
        "upstage",
        "information-extract",
    )

    assert [candidate for candidate in result["candidates"] if candidate["field"] == "ai"] == []
    features = [candidate["proposedValue"] for candidate in result["candidates"] if candidate["field"] == "features"]
    assert features == [["문서 요약"], ["개인화 추천"]]


def test_discards_a_generic_ai_label_instead_of_turning_it_into_a_feature():
    quote = "AI 기능을 제공한다."

    result = validate_grounding(
        {"items": [{"field": "ai", "value": ["AI"], "quote": quote, "context": "explicit"}]},
        ExtractedDocument("doc-1", "text", quote, (SourcePage(1, 0, len(quote)),)),
        "upstage",
        "information-extract",
    )

    assert result["candidates"] == []


def test_keeps_a_named_model_that_is_not_a_vendor_keyword_in_ai():
    quote = "BERT 모델을 활용한다."

    result = validate_grounding(
        {"items": [{"field": "ai", "value": ["BERT"], "quote": quote, "context": "explicit"}]},
        ExtractedDocument("doc-1", "text", quote, (SourcePage(1, 0, len(quote)),)),
        "upstage",
        "information-extract",
    )

    ai_candidates = [candidate["proposedValue"] for candidate in result["candidates"] if candidate["field"] == "ai"]
    assert ai_candidates == [["BERT"]]
