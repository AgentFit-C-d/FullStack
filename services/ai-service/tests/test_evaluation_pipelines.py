from app.evaluation.pipelines import SUPPORTED_PIPELINES, classify_pipeline_error
from evaluation.report import evaluate_directory, render_markdown
from pathlib import Path


def test_supported_pipeline_variants_are_explicit():
    assert SUPPORTED_PIPELINES == ("pypdf", "pymupdf", "ocrmypdf", "document-parse", "parse-ocr", "hybrid-fallback")


def test_pipeline_error_classifies_missing_text_layer():
    assert classify_pipeline_error(ValueError("NO_TEXT_LAYER")) == "TEXT_LAYER_MISSING"
    assert classify_pipeline_error(ValueError("INVALID_PDF")) == "INVALID_INPUT"
    assert classify_pipeline_error(ValueError("DEPENDENCY_NOT_INSTALLED:PyMuPDF")) == "DEPENDENCY_NOT_INSTALLED:PyMuPDF"
    assert classify_pipeline_error(ValueError("EXTERNAL_TOOL_MISSING:OCRmyPDF requires Tesseract and Ghostscript")) == "EXTERNAL_TOOL_MISSING:OCRmyPDF requires Tesseract and Ghostscript"


def test_report_compares_saved_pipeline_results():
    root = Path(__file__).parents[1] / "evaluation" / "test-fixtures"
    gold_dir, results_dir = root / "gold", root / "results"

    report = evaluate_directory(gold_dir, results_dir)

    assert report["pipelines"]["pypdf"]["quoteSuccessRate"] == 1.0
    assert "pypdf" in render_markdown(report)
