from app.analysis.canonical_document import render_canonical_document
from app.analysis.extraction import ExtractedDocument, SourcePage


def test_renders_korean_parse_output_as_a_canonical_pdf():
    document = ExtractedDocument(
        "doc-1",
        "markdown",
        "프로젝트 이름: AgentFit",
        (SourcePage(1, 0, len("프로젝트 이름: AgentFit")),),
    )

    canonical = render_canonical_document(document)

    assert canonical.data.startswith(b"%PDF-")
    assert len(canonical.pages) == 1
