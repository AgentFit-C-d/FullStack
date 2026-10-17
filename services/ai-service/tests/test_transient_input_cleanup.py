from datetime import datetime, timedelta, timezone

from app.ai_v2.analysis import AnalysisCore


def test_analysis_core_does_not_retain_document_or_extracted_text_after_return():
    core = AnalysisCore()
    result = core.analyze(
        "transient-1", "markdown", b"project_name: transient\ndomain: education",
        (datetime.now(timezone.utc) + timedelta(seconds=10)).isoformat().replace("+00:00", "Z"),
    )
    assert result["profile"]["project_name"] == "transient"
    assert "transient" not in result["modelMetadata"]
    assert not hasattr(core, "documents")
    assert not hasattr(core, "extracted_text")
