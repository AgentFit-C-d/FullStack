from app.analysis.grounding import _spans


def test_quote_search_ignores_whitespace_line_breaks_and_punctuation_differences():
    source = "설명: 참여형 · 보상\n제공"

    assert _spans(source, "참여형 보상 제공") == [(4, len(source))]


def test_quote_search_does_not_treat_different_words_as_a_match():
    assert _spans("참여형 보상 제공", "체크인 보상 제공") == []
