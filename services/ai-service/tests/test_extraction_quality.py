from app.analysis.extraction import ExtractedDocument, SourcePage, text_page_ratio


def test_text_page_ratio_allows_an_empty_cover_page_when_the_body_is_readable():
    document = ExtractedDocument(
        "doc-1",
        "pdf",
        "본문 텍스트",
        (
            SourcePage(1, 0, 0),
            SourcePage(2, 0, 5),
            SourcePage(3, 5, 10),
        ),
    )

    assert text_page_ratio(document) == 2 / 3


def test_text_page_ratio_is_zero_when_no_page_contains_text():
    document = ExtractedDocument("doc-1", "pdf", "", (SourcePage(1, 0, 0), SourcePage(2, 0, 0)))

    assert text_page_ratio(document) == 0
