from app.analysis.providers.document_parse import parse_document_response


def test_parse_skips_empty_figure_elements_but_keeps_text_elements():
    response = {
        "usage": {"pages": 1},
        "elements": [
            {"id": 1, "page": 1, "category": "figure", "content": {"html": "<figure><img src='image'/></figure>"}},
            {"id": 2, "page": 1, "category": "paragraph", "content": {"html": "<p>기획서 본문</p>"}},
        ],
    }

    document = parse_document_response(response, "doc-1", 1)

    assert document.text == "기획서 본문"


def test_parse_preserves_page_when_page_contains_only_figures():
    response = {
        "usage": {"pages": 2},
        "elements": [
            {"id": 1, "page": 1, "category": "figure", "content": {"html": "<figure><img src='image'/></figure>"}},
            {"id": 2, "page": 2, "category": "paragraph", "content": {"html": "<p>다음 페이지 본문</p>"}},
        ],
    }

    document = parse_document_response(response, "doc-2", 2)

    assert len(document.pages) == 2
    assert document.text.endswith("다음 페이지 본문")


def test_parse_falls_back_to_plain_text_for_complex_table_layout():
    response = {
        "usage": {"pages": 1},
        "elements": [
            {"id": 1, "page": 1, "category": "table", "content": {"html": "<table><tr><td colspan='2'>기능</td></tr><tr><td>로그인</td><td>OAuth</td></tr></table>"}},
        ],
    }

    document = parse_document_response(response, "doc-3", 1)

    assert "기능" in document.text
    assert "OAuth" in document.text


def test_parse_uses_provider_text_when_html_layout_is_not_supported():
    response = {
        "usage": {"pages": 1},
        "elements": [
            {"id": 1, "page": 1, "category": "chart", "content": {"html": "<chart><bad></chart>", "text": "차트 설명"}},
        ],
    }

    document = parse_document_response(response, "doc-4", 1)

    assert document.text == "차트 설명"
