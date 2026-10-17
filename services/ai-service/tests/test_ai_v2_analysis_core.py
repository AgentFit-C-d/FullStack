from datetime import datetime, timedelta, timezone
import base64
from pathlib import Path

import pytest

from app.ai_v2.analysis import AnalysisCore, MAX_BYTES, MAX_CODEPOINTS


def deadline():
    return (datetime.now(timezone.utc) + timedelta(seconds=10)).isoformat().replace("+00:00", "Z")


def test_text_analysis_returns_structured_profile_and_grounded_evidence():
    result = AnalysisCore().analyze(
        document_id="doc-1",
        kind="markdown",
        content="project_name: AgentFit\nfrontend: React\nfeatures: 추천 생성".encode(),
        deadline=deadline(),
    )

    assert result["profile"]["project_name"] == "AgentFit"
    assert result["profile"]["frontend"] == ["React"]
    assert result["profile"]["features"] == ["추천 생성"]
    assert result["candidates"]
    assert result["candidates"][0]["evidence"][0]["documentId"] == "doc-1"
    assert "AgentFit" not in result["modelMetadata"]


@pytest.mark.parametrize("kind", ["markdown", "text"])
def test_analysis_accepts_markdown_and_text_with_the_same_limits(kind):
    result = AnalysisCore().analyze(
        document_id="doc-2", kind=kind, content="domain: education".encode(), deadline=deadline()
    )
    assert result["profile"]["domain"] == "education"


def test_analysis_rejects_unsupported_format_and_expired_deadline():
    with pytest.raises(ValueError, match="UNSUPPORTED_FORMAT"):
        AnalysisCore().analyze("doc-3", "docx", b"x", deadline())
    with pytest.raises(ValueError, match="ANALYSIS_TIMEOUT"):
        AnalysisCore().analyze("doc-3", "text", b"x", "2020-01-01T00:00:00Z")


def test_analysis_rejects_untrusted_or_missing_quotes():
    with pytest.raises(ValueError, match="INVALID_PROVIDER_OUTPUT"):
        AnalysisCore(provider=lambda _: {"items": [{"field": "domain", "value": "health", "quote": "not in document", "context": "explicit"}]}).analyze(
            "doc-4", "text", "domain: education".encode(), deadline()
        )


def pdf_fixture(text: str) -> bytes:
    content = f"BT\n/F1 12 Tf\n72 720 Td\n({text}) Tj\nET\n".encode()
    objects = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
        b"<< /Length " + str(len(content)).encode() + b" >>\nstream\n" + content + b"endstream",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
    ]
    document = b"%PDF-1.4\n"
    offsets = [0]
    for index, value in enumerate(objects, 1):
        offsets.append(len(document))
        document += f"{index} 0 obj\n".encode() + value + b"\nendobj\n"
    xref = len(document)
    document += f"xref\n0 {len(objects) + 1}\n0000000000 65535 f \n".encode()
    document += b"".join(f"{offset:010d} 00000 n \n".encode() for offset in offsets[1:])
    document += f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode()
    return document


def test_pdf_extraction_and_input_limits_are_enforced():
    result = AnalysisCore().analyze("doc-pdf", "pdf", pdf_fixture("domain: education"), deadline())
    assert result["profile"]["domain"] == "education"
    with pytest.raises(ValueError, match="FILE_TOO_LARGE"):
        AnalysisCore().analyze("doc-large", "text", b"x" * (MAX_BYTES + 1), deadline())
    with pytest.raises(ValueError, match="TEXT_TOO_LONG"):
        AnalysisCore().analyze("doc-long", "text", ("x" * (MAX_CODEPOINTS + 1)).encode(), deadline())


def test_repository_pdf_fixture_is_decoded_only_at_the_request_boundary():
    encoded = (Path(__file__).parent / "fixtures" / "ai_v2" / "analysis.pdf.b64").read_text().strip()
    result = AnalysisCore().analyze("fixture-pdf", "pdf", base64.b64decode(encoded), deadline())
    assert result["profile"]["domain"] == "education"
    assert not hasattr(AnalysisCore(), "documents")
