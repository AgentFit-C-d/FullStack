from app.analysis.grounding import related_value_spans


def test_finds_a_related_page_span_when_the_ai_quote_does_not_match():
    text = "첫 페이지\f\n두 번째 페이지의 참여형 보상 기능"

    assert related_value_spans(text, ["참여형 보상"]) == [(17, 23)]


def test_requires_every_value_for_a_multi_value_candidate():
    text = "React만 문서에 있습니다."

    assert related_value_spans(text, ["React", "Vite"]) == []
